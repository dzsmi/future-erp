// 작성: (주)핀링크 송태호 / 2021.06.21
/*
 * 전자세금계산서 발행 (매출)
 *  - createFrETaxInvoice      : 작성중(FRTI_DRAFT) 헤더 생성 (FrTaxInvoice, eTaxYn=Y)
 *  - addFrETaxInvoiceItem     : 품목 추가 → 합계 재계산
 *  - removeFrETaxInvoiceItem  : 품목 삭제 → 합계 재계산
 *  - issueFrETaxInvoice       : 합계 재계산 후 ASP 발행 요청, 승인번호 수신 시 등록(FRTI_REGISTERED)
 *
 * 금액 계산 (국세청 전자세금계산서 기준 원 단위 반올림)
 *  - 품목 공급가액 = 수량 × 단가, 원 단위 반올림(HALF_UP)
 *  - 품목 세액     = 품목 공급가액 × 10%, 원 단위 반올림(HALF_UP)
 *  - 합계          = 품목 합
 *
 * ASP : fracct.etax.aspUrl (JSON POST), fracct.etax.testMode=Y 이면 호출 없이 가승인번호
 */

import java.math.RoundingMode
import java.sql.Timestamp

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtilProperties

VAT_RATE = new BigDecimal("0.1")

def createFrETaxInvoice() {
    String taxInvoiceId = "TI" + delegator.getNextSeqId("FrTaxInvoice")
    GenericValue taxInvoice = delegator.makeValue("FrTaxInvoice", [
            taxInvoiceId        : taxInvoiceId,
            taxInvoiceTypeEnumId: "FRTI_SALES",
            partyId             : parameters.partyId,
            issueDate           : parameters.issueDate,
            supplyAmount        : BigDecimal.ZERO,
            vatAmount           : BigDecimal.ZERO,
            totalAmount         : BigDecimal.ZERO,
            statusId            : "FRTI_DRAFT",
            description         : parameters.description,
            eTaxYn              : "Y",
            buyerEmail          : parameters.buyerEmail,
            etaxSendStatus      : "READY",
            createdByUserLogin  : userLogin.userLoginId])
    taxInvoice.create()

    Map result = success()
    result.taxInvoiceId = taxInvoiceId
    return result
}

def addFrETaxInvoiceItem() {
    GenericValue taxInvoice = from("FrTaxInvoice").where(taxInvoiceId: parameters.taxInvoiceId).queryOne()
    String err = checkDraft(taxInvoice)
    if (err) return error(err)

    BigDecimal quantity = parameters.quantity ?: BigDecimal.ONE
    BigDecimal unitPrice = parameters.unitPrice
    BigDecimal supplyAmount = quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP)
    BigDecimal vatAmount = supplyAmount.multiply(VAT_RATE).setScale(0, RoundingMode.HALF_UP)

    GenericValue item = delegator.makeValue("FrETaxInvoiceItem", [
            taxInvoiceId: taxInvoice.taxInvoiceId,
            supplyDate  : parameters.supplyDate ?: taxInvoice.issueDate,
            itemName    : parameters.itemName,
            itemSpec    : parameters.itemSpec,
            quantity    : quantity,
            unitPrice   : unitPrice,
            supplyAmount: supplyAmount,
            vatAmount   : vatAmount,
            remark      : parameters.remark])
    delegator.setNextSubSeqId(item, "itemSeqId", 5, 1)
    item.create()

    calcFrETaxTotals(taxInvoice)
    return success()
}

def removeFrETaxInvoiceItem() {
    GenericValue taxInvoice = from("FrTaxInvoice").where(taxInvoiceId: parameters.taxInvoiceId).queryOne()
    String err = checkDraft(taxInvoice)
    if (err) return error(err)

    GenericValue item = from("FrETaxInvoiceItem").where(taxInvoiceId: parameters.taxInvoiceId, itemSeqId: parameters.itemSeqId).queryOne()
    if (item) item.remove()

    calcFrETaxTotals(taxInvoice)
    return success()
}

def issueFrETaxInvoice() {
    String module = "FrETaxServices.groovy"
    GenericValue taxInvoice = from("FrTaxInvoice").where(taxInvoiceId: parameters.taxInvoiceId).queryOne()
    String err = checkDraft(taxInvoice)
    if (err) return error(err)

    List<GenericValue> items = from("FrETaxInvoiceItem").where(taxInvoiceId: taxInvoice.taxInvoiceId).orderBy("itemSeqId").queryList()
    if (!items) {
        return error(UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctETaxNoItem", locale))
    }
    calcFrETaxTotals(taxInvoice)

    GenericValue buyer = from("PartyGroup").where(partyId: taxInvoice.partyId).queryOne()
    Map req = [corpNum     : EntityUtilProperties.getPropertyValue("fracct", "fracct.etax.corpNum", delegator),
               invoiceKey  : taxInvoice.taxInvoiceId,
               writeDate   : taxInvoice.issueDate.toString().replace("-", ""),
               buyerCorpNum: buyer?.federalTaxId,
               buyerName   : buyer?.groupName,
               buyerEmail  : taxInvoice.buyerEmail,
               supplyTotal : taxInvoice.supplyAmount.toPlainString(),
               taxTotal    : taxInvoice.vatAmount.toPlainString(),
               total       : taxInvoice.totalAmount.toPlainString(),
               items       : items.collect { [date : it.supplyDate?.toString()?.replace("-", ""), name: it.itemName, spec: it.itemSpec,
                                              qty  : it.quantity?.toPlainString(), unitCost: it.unitPrice?.toPlainString(),
                                              supply: it.supplyAmount.toPlainString(), tax: it.vatAmount.toPlainString()] }]

    Map res = sendToAsp(req)
    taxInvoice.etaxSendDate = UtilDateTime.nowTimestamp()
    taxInvoice.etaxResultMsg = res.message
    if (!res.success) {
        taxInvoice.etaxSendStatus = "FAIL"
        taxInvoice.store()
        Debug.logWarning("전자세금계산서 발행 실패 ${taxInvoice.taxInvoiceId} : ${res.message}", module)
        return error(UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctETaxIssueFail", [message: res.message], locale))
    }
    taxInvoice.taxInvoiceNo = res.ntsConfirmNum
    taxInvoice.etaxSendStatus = "SENT"
    taxInvoice.statusId = "FRTI_REGISTERED"
    taxInvoice.store()

    Map result = success()
    result.taxInvoiceNo = res.ntsConfirmNum
    return result
}

/*
 * 품목 합계 → 헤더 공급가액/세액/합계
 */
void calcFrETaxTotals(GenericValue taxInvoice) {
    List<GenericValue> items = from("FrETaxInvoiceItem").where(taxInvoiceId: taxInvoice.taxInvoiceId).queryList()
    BigDecimal supplyTotal = BigDecimal.ZERO
    BigDecimal vatTotal = BigDecimal.ZERO
    for (GenericValue item : items) {
        supplyTotal = supplyTotal.add(item.supplyAmount)
        vatTotal = vatTotal.add(item.vatAmount)
    }
    taxInvoice.supplyAmount = supplyTotal
    taxInvoice.vatAmount = vatTotal
    taxInvoice.totalAmount = supplyTotal.add(vatTotal)
    taxInvoice.store()
}

String checkDraft(GenericValue taxInvoice) {
    if (taxInvoice == null) {
        return UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctTaxInvoiceNotFound", locale)
    }
    if ("Y" != taxInvoice.eTaxYn || "FRTI_DRAFT" != taxInvoice.statusId) {
        return UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctETaxNotDraft", locale)
    }
    return null
}

Map sendToAsp(Map req) {
    String module = "FrETaxServices.groovy"
    if ("Y" == EntityUtilProperties.getPropertyValue("fracct", "fracct.etax.testMode", "N", delegator)) {
        String fakeNo = UtilDateTime.nowAsString().substring(0, 8) + "-TEST-" + req.invoiceKey
        return [success: true, ntsConfirmNum: fakeNo, message: "TEST MODE"]
    }

    String aspUrl = EntityUtilProperties.getPropertyValue("fracct", "fracct.etax.aspUrl", delegator)
    String apiKey = EntityUtilProperties.getPropertyValue("fracct", "fracct.etax.apiKey", delegator)
    int timeout = EntityUtilProperties.getPropertyAsInteger("fracct", "fracct.etax.timeoutMs", 15000)
    HttpURLConnection conn = null
    try {
        conn = (HttpURLConnection) new URL(aspUrl).openConnection()
        conn.setRequestMethod("POST")
        conn.setConnectTimeout(timeout)
        conn.setReadTimeout(timeout)
        conn.setDoOutput(true)
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("X-Api-Key", apiKey)
        conn.outputStream.withWriter("UTF-8") { it.write(JsonOutput.toJson(req)) }

        int code = conn.responseCode
        String body = (code < 400 ? conn.inputStream : conn.errorStream)?.getText("UTF-8")
        Map resp = body ? (Map) new JsonSlurper().parseText(body) : [:]
        if (code == 200 && resp.code == "0000") {
            return [success: true, ntsConfirmNum: resp.ntsConfirmNum, message: resp.message]
        }
        return [success: false, message: "[${code}/${resp.code}] ${resp.message}"]
    } catch (Exception e) {
        Debug.logError(e, "전자세금계산서 ASP 호출 오류", module)
        return [success: false, message: e.toString()]
    } finally {
        conn?.disconnect()
    }
}
