// 작성: 오픈브릿지시스템즈 박준영 / 2018.07.09
/*
 * 연차 잔여 조회 화면 : 재직자별 getFrLeaveBalance 결과 목록
 */

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

println "########## LeaveBalanceList.groovy start"
Long grantYear = parameters.grantYear ? (parameters.grantYear as Long) : (Calendar.getInstance().get(Calendar.YEAR) as Long)
context.grantYear = grantYear

List conds = [EntityCondition.makeCondition("retireDate", EntityOperator.EQUALS, null)]
if (parameters.partyId) {
    conds.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId))
}
List<GenericValue> emplInfos = from("FrEmplInfo").where(EntityCondition.makeCondition(conds, EntityOperator.AND)).orderBy("emplNo").queryList()

// 예상 발생 연차 기준일 (조회년도 12/31, 올해면 오늘)
java.time.LocalDate today = java.time.LocalDate.now()
java.time.LocalDate expectBaseDate = java.time.LocalDate.of(grantYear.intValue(), 12, 31)
if (expectBaseDate.isAfter(today)) expectBaseDate = today
context.expectBaseDate = java.sql.Date.valueOf(expectBaseDate)

List balanceList = []
for (GenericValue emplInfo : emplInfos) {
    Map balance = dispatcher.runSync("getFrLeaveBalance", [partyId: emplInfo.partyId, grantYear: grantYear, userLogin: userLogin])

    // 예상 발생 연차
    int expectGrantDays = 0
    if (emplInfo.hireDate) {
        java.time.LocalDate hire = emplInfo.hireDate.toLocalDate()
        int years = java.time.Period.between(hire, expectBaseDate).getYears()
        int months = (int) java.time.temporal.ChronoUnit.MONTHS.between(hire, expectBaseDate)
        if (years < 1) {
            expectGrantDays = months
        } else {
            expectGrantDays = 15 + (int) ((years - 1) / 2)
            if (expectGrantDays > 25) expectGrantDays = 25
        }
        if (expectGrantDays < 0) expectGrantDays = 0
    }
    balanceList.add([partyId      : emplInfo.partyId,
                     emplNo       : emplInfo.emplNo,
                     hireDate     : emplInfo.hireDate,
                     grantedDays  : balance.grantedDays,
                     usedDays     : balance.usedDays,
                     requestedDays: balance.requestedDays,
                     remainDays   : balance.remainDays,
                     expectGrantDays: expectGrantDays])
}
context.balanceList = balanceList
