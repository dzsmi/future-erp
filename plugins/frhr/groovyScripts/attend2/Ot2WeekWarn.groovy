// 2022.10.17 임재훈
// VOC: 연장근로 주 12시간 넘게 신청됨 (인사팀)
// 임시조치 - 신청 화면에 그 주 연장근로 합계(휴일 포함) 보여주고 12시간 넘으면 경고만 (신청은 됨)
// requestOt2 서비스 검사는 건드리지 않음 (승인 화면 집계 Ot2ApproveList.groovy 와 같은 기준으로 맞춤)
// TODO 원인 파악 필요 (재현안됨) - 서비스에서 왜 통과되는지 확인 못함

import java.sql.Date
import java.time.DayOfWeek
import java.time.LocalDate

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

String warnPartyId = parameters.partyId
LocalDate baseDate = LocalDate.now()
try {
    if (parameters.otDate) baseDate = LocalDate.parse(parameters.otDate.toString().substring(0, 10))
} catch (Exception e) {
    baseDate = LocalDate.now()
}
LocalDate mon = baseDate.with(DayOfWeek.MONDAY)
LocalDate sun = mon.plusDays(6)

BigDecimal weekOtTotal = BigDecimal.ZERO
BigDecimal weekHoliTotal = BigDecimal.ZERO
if (warnPartyId) {
    def otList = from("FrAttend2Ot").where(EntityCondition.makeCondition([
            EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, warnPartyId),
            EntityCondition.makeCondition("reqStatus", EntityOperator.IN, ["REQ", "APPR"]),
            EntityCondition.makeCondition("otDate", EntityOperator.GREATER_THAN_EQUAL_TO, Date.valueOf(mon)),
            EntityCondition.makeCondition("otDate", EntityOperator.LESS_THAN_EQUAL_TO, Date.valueOf(sun))
    ], EntityOperator.AND)).queryList()
    for (ot in otList) {
        weekOtTotal = weekOtTotal + (ot.otHr ?: 0)          // 평일 + 휴일 전부
        if ("HOLIDAY".equals(ot.otType)) weekHoliTotal = weekHoliTotal + (ot.otHr ?: 0)
    }
    println "### Ot2WeekWarn " + warnPartyId + " " + mon + "~" + sun + " total=" + weekOtTotal + " holi=" + weekHoliTotal
}

context.warnPartyId = warnPartyId
context.weekFrom = mon.toString()
context.weekTo = sun.toString()
context.weekOtTotal = weekOtTotal
context.weekHoliTotal = weekHoliTotal
context.weekOtLimit = 12
context.weekOtOver = weekOtTotal > 12 ? "Y" : "N"
