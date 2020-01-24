import java.sql.Date
import java.time.DayOfWeek
import java.time.LocalDate

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

// 연장근로 승인 화면 - 주간 집계
LocalDate base = parameters.weekDate ? Date.valueOf(parameters.weekDate.toString().substring(0, 10)).toLocalDate() : LocalDate.now()
LocalDate mon = base.with(DayOfWeek.MONDAY)
LocalDate sun = mon.plusDays(6)
context.weekFrom = Date.valueOf(mon)
context.weekThru = Date.valueOf(sun)

conds = [
  EntityCondition.makeCondition("reqStatus", EntityOperator.IN, ["REQ", "APPR"]),
  EntityCondition.makeCondition("otDate", EntityOperator.GREATER_THAN_EQUAL_TO, Date.valueOf(mon)),
  EntityCondition.makeCondition("otDate", EntityOperator.LESS_THAN_EQUAL_TO, Date.valueOf(sun))
]
if (parameters.partyId) {
  conds.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId))
}
weekOts = from("FrAttend2Ot").where(EntityCondition.makeCondition(conds, EntityOperator.AND)).orderBy("partyId", "otDate").queryList()

// 직원별 주간 합계 (연장+휴일)
weekTotal = [:]
weekOts.each { ot ->
  weekTotal[ot.partyId] = (weekTotal[ot.partyId] ?: BigDecimal.ZERO) + (ot.otHr ?: BigDecimal.ZERO)
}
println "ot2 approve week >>> " + mon + "~" + sun + " " + weekTotal

ot2List = []
weekOts.each { ot ->
  if (parameters.showAll != "Y" && ot.reqStatus != "REQ") return
  BigDecimal tot = weekTotal[ot.partyId]
  ot2List.add([otReqId: ot.otReqId, partyId: ot.partyId, otDate: ot.otDate, otType: ot.otType,
    startTime: ot.startTime, endTime: ot.endTime, otHr: ot.otHr, reason: ot.reason, reqStatus: ot.reqStatus,
    weekTotal: tot, overYn: (tot > 12 ? "Y" : "N")])
}
context.ot2List = ot2List
