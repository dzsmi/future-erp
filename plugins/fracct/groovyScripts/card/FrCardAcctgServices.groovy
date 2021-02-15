// 작성: (주)핀링크 송태호 / 2021.02.15
/*
 * 법인카드 사용내역 → 회계 거래 자동 생성
 *  - 대상 : 수신(FRCT_RECEIVED) / 소명완료(FRCT_EXPLAINED) 이고 승인취소 안 된 건
 *           소명필요(FRCT_EXPLAIN_REQ) 건은 소명 완료 후 다음 배치에서 생성
 *  - 사용내역 1건 = AcctgTrans 1건 (거래일 = 카드 승인일시)
 *      차) 비용     : 카드별 기본 비용계정(FrCardMaster.expenseGlAccountId), 없으면 fracct.card.gl.expense   공급가액
 *      차) 부가세대급금 : fracct.card.gl.vat                                                       부가세 (0원이면 생략)
 *      대) 미지급금   : fracct.card.gl.payable                                                   승인금액
 *  - 코어 createAcctgTrans / createAcctgTransEntry 직접 호출 (전표 결재 대상 아님 - 카드 사용은 사후 정산)
 *  - 스케줄 FR_CARD_SLIP 매일 07:30 (수신 07:00 이후), 화면 [자동전표 생성] 버튼으로 수동 실행
 */

import java.sql.Timestamp

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtilProperties
import org.apache.ofbiz.service.ServiceUtil

def createFrCardAcctgTransBatch() {
    String module = "FrCardAcctgServices.groovy"

    String organizationPartyId = EntityUtilProperties.getPropertyValue("fracct", "fracct.organizationPartyId", "FR_COMPANY", delegator)
    String acctgTransTypeId = EntityUtilProperties.getPropertyValue("fracct", "fracct.acctgTransTypeId", "INTERNAL_ACCTG_TRANS", delegator)
    String glFiscalTypeId = EntityUtilProperties.getPropertyValue("fracct", "fracct.glFiscalTypeId", "ACTUAL", delegator)
    String acctgTransEntryTypeId = EntityUtilProperties.getPropertyValue("fracct", "fracct.acctgTransEntryTypeId", "_NA_", delegator)
    String currencyUomId = EntityUtilProperties.getPropertyValue("fracct", "fracct.currencyUomId", "KRW", delegator)
    String defaultExpenseGl = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.gl.expense", delegator)
    String vatGl = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.gl.vat", delegator)
    String payableGl = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.gl.payable", delegator)
    String cardCompanyPartyId = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.cardCompanyPartyId", delegator)

    List conds = [EntityCondition.makeCondition("statusId", EntityOperator.IN, ["FRCT_RECEIVED", "FRCT_EXPLAINED"]),
                  EntityCondition.makeCondition("cancelYn", EntityOperator.NOT_EQUAL, "Y"),
                  EntityCondition.makeCondition("acctgTransId", null)]
    if (parameters.cardTxId) conds.add(EntityCondition.makeCondition("cardTxId", parameters.cardTxId))
    List<GenericValue> cardTxList = from("FrCardTx").where(EntityCondition.makeCondition(conds, EntityOperator.AND))
            .orderBy("txDate").queryList()

    int postedCnt = 0
    List<String> errors = []
    for (GenericValue cardTx : cardTxList) {
        GenericValue card = from("FrCardMaster").where(cardNo: cardTx.cardNo).queryOne()
        if (card != null && "N" == card.useYn) {
            errors.add("${cardTx.cardTxId} : 사용중지 카드 ${cardTx.cardNo}")
            continue
        }
        String expenseGl = card?.expenseGlAccountId ?: defaultExpenseGl
        BigDecimal amount = cardTx.amount ?: BigDecimal.ZERO
        BigDecimal vatAmount = cardTx.vatAmount ?: BigDecimal.ZERO
        BigDecimal expenseAmount = amount.subtract(vatAmount)    // 면세/봉사료 포함 건은 공급가액과 다를 수 있어 승인금액 - 부가세

        // 1. 회계 거래 헤더 (거래일 = 승인일시)
        Map transRes = dispatcher.runSync("createAcctgTrans", [
                acctgTransTypeId: acctgTransTypeId,
                glFiscalTypeId  : glFiscalTypeId,
                transactionDate : cardTx.txDate,
                partyId         : card?.holderPartyId,
                description     : "[법인카드] " + (cardTx.merchantName ?: "") + " / 승인 " + cardTx.approvalNo,
                userLogin       : userLogin])
        if (ServiceUtil.isError(transRes)) {
            errors.add("${cardTx.cardTxId} : " + ServiceUtil.getErrorMessage(transRes))
            continue
        }
        String acctgTransId = transRes.acctgTransId

        // 2. 회계 거래 항목
        List<Map> entries = []
        entries.add([glAccountId: expenseGl, debitCreditFlag: "D", amount: expenseAmount, partyId: card?.deptPartyId])
        if (vatAmount.signum() > 0) {
            entries.add([glAccountId: vatGl, debitCreditFlag: "D", amount: vatAmount, partyId: null])
        }
        entries.add([glAccountId: payableGl, debitCreditFlag: "C", amount: amount, partyId: cardCompanyPartyId])

        boolean entryError = false
        for (Map entry : entries) {
            Map entryRes = dispatcher.runSync("createAcctgTransEntry", [
                    acctgTransId         : acctgTransId,
                    acctgTransEntryTypeId: acctgTransEntryTypeId,
                    organizationPartyId  : organizationPartyId,
                    glAccountId          : entry.glAccountId,
                    debitCreditFlag      : entry.debitCreditFlag,
                    amount               : entry.amount,
                    origAmount           : entry.amount,
                    currencyUomId        : currencyUomId,
                    origCurrencyUomId    : currencyUomId,
                    partyId              : entry.partyId,
                    voucherRef           : cardTx.cardTxId,
                    description          : cardTx.cardNo + " " + (cardTx.merchantName ?: ""),
                    userLogin            : userLogin])
            if (ServiceUtil.isError(entryRes)) {
                errors.add("${cardTx.cardTxId} : " + ServiceUtil.getErrorMessage(entryRes))
                entryError = true
                break
            }
        }
        if (entryError) continue

        // 3. 사용내역에 회계 거래 연결
        cardTx.acctgTransId = acctgTransId
        cardTx.statusId = "FRCT_POSTED"
        cardTx.lastUpdatedByUserLogin = userLogin?.userLoginId
        cardTx.store()
        postedCnt++
    }

    Debug.logInfo("법인카드 자동전표 : 대상 ${cardTxList.size()}건 / 생성 ${postedCnt}건 / 오류 ${errors.size()}건", module)
    if (errors) {
        Debug.logWarning("법인카드 자동전표 오류\n" + errors.join("\n"), module)
    }

    Map result = success("법인카드 자동전표 ${postedCnt}건 생성" + (errors ? " / 오류 ${errors.size()}건" : ""))
    result.postedCnt = postedCnt
    result.errorList = errors
    return result
}
