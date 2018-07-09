// 작성: 오픈브릿지시스템즈 박준영 / 2018.07.09
/*
 * 연차 잔여 조회 화면 : 재직자별 getFrLeaveBalance 결과 목록
 */

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

Long grantYear = parameters.grantYear ? (parameters.grantYear as Long) : (Calendar.getInstance().get(Calendar.YEAR) as Long)
context.grantYear = grantYear

List conds = [EntityCondition.makeCondition("retireDate", EntityOperator.EQUALS, null)]
if (parameters.partyId) {
    conds.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId))
}
List<GenericValue> emplInfos = from("FrEmplInfo").where(EntityCondition.makeCondition(conds, EntityOperator.AND)).orderBy("emplNo").queryList()

List balanceList = []
for (GenericValue emplInfo : emplInfos) {
    Map balance = dispatcher.runSync("getFrLeaveBalance", [partyId: emplInfo.partyId, grantYear: grantYear, userLogin: userLogin])
    balanceList.add([partyId      : emplInfo.partyId,
                     emplNo       : emplInfo.emplNo,
                     hireDate     : emplInfo.hireDate,
                     grantedDays  : balance.grantedDays,
                     usedDays     : balance.usedDays,
                     requestedDays: balance.requestedDays,
                     remainDays   : balance.remainDays])
}
context.balanceList = balanceList
