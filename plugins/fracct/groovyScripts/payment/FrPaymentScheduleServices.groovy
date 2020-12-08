// 2019.01.21 정해인 - 거래처 지급예정표 (재무팀 요청)
// 지급기일 = 세금계산서 발행일 + 60일

import java.sql.Date
import java.time.LocalDate

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def getFrPaymentSchedule() {
    println "### getFrPaymentSchedule 진입 : " + parameters
    LocalDate fromDue = parameters.fromDueDate ? parameters.fromDueDate.toLocalDate() : LocalDate.now()
    LocalDate thruDue = parameters.thruDueDate ? parameters.thruDueDate.toLocalDate() : fromDue.plusDays(60)

    // 발행일 + 60일 이 조회기간에 들어오는 매입 세금계산서
    List conds = [
        EntityCondition.makeCondition("taxInvoiceTypeEnumId", EntityOperator.EQUALS, "FRTI_PURCHASE"),
        EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "FRTI_REGISTERED"),
        EntityCondition.makeCondition("issueDate", EntityOperator.GREATER_THAN_EQUAL_TO, Date.valueOf(fromDue.minusDays(60))),
        EntityCondition.makeCondition("issueDate", EntityOperator.LESS_THAN_EQUAL_TO, Date.valueOf(thruDue.minusDays(60)))
    ]
    if (parameters.partyId) {
        conds.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId))
    }
    List<GenericValue> taxInvoices = from("FrTaxInvoice").where(EntityCondition.makeCondition(conds, EntityOperator.AND))
            .orderBy("issueDate", "partyId").queryList()

    List scheduleList = []
    BigDecimal totalAmount = BigDecimal.ZERO
    for (GenericValue taxInvoice : taxInvoices) {
        GenericValue paymentPlan = from("FrPaymentPlan").where(taxInvoiceId: taxInvoice.taxInvoiceId).queryFirst()
        String payStatusId = paymentPlan?.statusId
        if ("FRPP_CANCELLED".equals(payStatusId)) continue
        if ("FRPP_PAID".equals(payStatusId) && !"Y".equals(parameters.includePaid)) continue

        Date dueDate = Date.valueOf(taxInvoice.issueDate.toLocalDate().plusDays(60))
        BigDecimal amount = taxInvoice.totalAmount ?: BigDecimal.ZERO
        scheduleList.add([dueDate      : dueDate,
                          partyId      : taxInvoice.partyId,
                          taxInvoiceId : taxInvoice.taxInvoiceId,
                          taxInvoiceNo : taxInvoice.taxInvoiceNo,
                          issueDate    : taxInvoice.issueDate,
                          supplyAmount : taxInvoice.supplyAmount,
                          vatAmount    : taxInvoice.vatAmount,
                          amount       : amount,
                          paymentPlanId: paymentPlan?.paymentPlanId,
                          statusId     : payStatusId])
        totalAmount = totalAmount.add(amount)
    }
    scheduleList.sort { a, b -> a.dueDate <=> b.dueDate ?: a.partyId <=> b.partyId }

    Map result = success()
    result.scheduleList = scheduleList
    result.totalAmount = totalAmount
    return result
}
