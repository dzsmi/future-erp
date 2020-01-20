import java.sql.Date
import java.sql.Timestamp
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def isHoliday(LocalDate d, String holidayYn) {
  if ("Y".equals(holidayYn)) return true
  return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY
}

def hours(Timestamp from, Timestamp to) {
  return new BigDecimal(to.getTime() - from.getTime()).divide(new BigDecimal(3600000), 2, RoundingMode.HALF_UP)
}

/*
 * 출퇴근 저장
 * 휴게 : 4시간 넘으면 30분, 8시간 넘으면 1시간
 * 평일 8시간 넘는 부분 = otHr, 휴일은 전부 holiHr
 */
def saveAttend2() {
  def att2 = null
  if (parameters.att2Id) {
    att2 = from("FrAttend2").where("att2Id", parameters.att2Id).queryOne()
  }
  if (att2 == null) {
    att2 = from("FrAttend2").where("partyId", parameters.partyId, "attDate", parameters.attDate).queryFirst()
  }
  boolean isNew = false
  if (att2 == null) {
    att2 = delegator.makeValue("FrAttend2")
    att2.att2Id = delegator.getNextSeqId("FrAttend2")
    att2.partyId = parameters.partyId
    att2.attDate = parameters.attDate
    isNew = true
  }
  att2.inTime = parameters.inTime
  att2.outTime = parameters.outTime
  att2.att2Type = parameters.att2Type ?: "NORMAL"
  att2.memo = parameters.memo

  LocalDate d = parameters.attDate.toLocalDate()
  boolean holi = isHoliday(d, parameters.holidayYn)
  att2.holidayYn = holi ? "Y" : "N"

  BigDecimal work = BigDecimal.ZERO
  if (att2.inTime && att2.outTime) {
    if (!att2.outTime.after(att2.inTime)) {
      return error("퇴근시간이 출근시간보다 빠릅니다.")
    }
    BigDecimal span = hours(att2.inTime, att2.outTime)
    if (span > 8) work = span - 1
    else if (span > 4) work = span - 0.5
    else work = span
  }
  att2.workHr = work
  if (holi) {
    att2.holiHr = work
    att2.otHr = BigDecimal.ZERO
  } else {
    att2.holiHr = BigDecimal.ZERO
    att2.otHr = work > 8 ? work - 8 : BigDecimal.ZERO
  }
  println "att2 save >>> " + att2.partyId + " " + att2.attDate + " work=" + work + " holi=" + holi
  if (isNew) att2.create()
  else att2.store()

  def result = success()
  result.att2Id = att2.att2Id
  return result
}

/*
 * 연장근로 신청
 * 같은 주(월~일) 평일 연장 신청시간(신청+승인) 합이 12시간 넘으면 오류
 * 휴일근로는 연장 한도에 안 넣음
 */
def requestOt2() {
  BigDecimal WEEK_OT_LIMIT = new BigDecimal("12") // 주 연장근로 한도 (근기법 53조)
  LocalDate d = parameters.otDate.toLocalDate()
  if (!parameters.endTime.after(parameters.startTime)) {
    return error("종료시간이 시작시간보다 빠릅니다.")
  }
  BigDecimal otHr = hours(parameters.startTime, parameters.endTime)
  String otType = isHoliday(d, parameters.holidayYn) ? "HOLIDAY" : "WEEKDAY"

  BigDecimal weekSum = BigDecimal.ZERO
  if ("WEEKDAY".equals(otType)) {
    LocalDate mon = d.with(DayOfWeek.MONDAY)
    LocalDate sun = mon.plusDays(6)
    def list = from("FrAttend2Ot").where(EntityCondition.makeCondition([
        EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId),
        EntityCondition.makeCondition("otType", EntityOperator.EQUALS, "WEEKDAY"),
        EntityCondition.makeCondition("reqStatus", EntityOperator.IN, ["REQ", "APPR"]),
        EntityCondition.makeCondition("otDate", EntityOperator.GREATER_THAN_EQUAL_TO, Date.valueOf(mon)),
        EntityCondition.makeCondition("otDate", EntityOperator.LESS_THAN_EQUAL_TO, Date.valueOf(sun))
      ], EntityOperator.AND)).queryList()
    for (ot in list) {
      weekSum = weekSum + (ot.otHr ?: 0)
    }
    println "ot2 week sum >>> " + parameters.partyId + " " + mon + "~" + sun + " " + weekSum + " + " + otHr
    if (weekSum + otHr > WEEK_OT_LIMIT) {
      return error("주 연장근로 12시간을 초과합니다. (이번주 신청 " + weekSum + "시간, 이번 신청 " + otHr + "시간)")
    }
  }

  def ot = delegator.makeValue("FrAttend2Ot")
  ot.otReqId = delegator.getNextSeqId("FrAttend2Ot")
  ot.partyId = parameters.partyId
  ot.otDate = parameters.otDate
  ot.otType = otType
  ot.startTime = parameters.startTime
  ot.endTime = parameters.endTime
  ot.otHr = otHr
  ot.reason = parameters.reason
  ot.reqStatus = "REQ"
  ot.create()

  def result = success("연장근로 신청 완료")
  result.otReqId = ot.otReqId
  result.weekOtHr = weekSum + ("WEEKDAY".equals(otType) ? otHr : 0)
  return result
}

def cancelOt2() {
  def ot = from("FrAttend2Ot").where("otReqId", parameters.otReqId).queryOne()
  if (ot == null) return error("신청건 없음")
  if (!"REQ".equals(ot.reqStatus)) return error("신청 상태만 취소 가능")
  ot.remove()
  return success()
}
