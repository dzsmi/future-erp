import java.sql.Date
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

/*
 * 월 근태 마감
 * - 직원별 한달 연장/휴일근로 시간 집계 -> FrAttend2Close
 * - 수당 = 통상시급(기본급/209) x 1.5 x (연장 + 휴일)
 * - 다시 돌리면 덮어씀
 */
def closeAttend2Month() {
  DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMM")
  YearMonth ym = parameters.closeYm ? YearMonth.parse(parameters.closeYm, fmt) : YearMonth.now().minusMonths(1)
  Date monthStart = Date.valueOf(ym.atDay(1))
  Date monthEnd = Date.valueOf(ym.atEndOfMonth())
  String closeYm = ym.format(fmt)
  println "att2 close >>> " + closeYm + " " + monthStart + "~" + monthEnd

  BigDecimal stdHours = new BigDecimal(UtilProperties.getPropertyValue("frhr", "payroll.standardMonthlyHours", "209"))
  BigDecimal factor = new BigDecimal(UtilProperties.getPropertyValue("frhr", "payroll.overtimeFactor", "1.5"))

  def emplConds = [
    EntityCondition.makeCondition("hireDate", EntityOperator.LESS_THAN_EQUAL_TO, monthEnd),
    EntityCondition.makeCondition(
      EntityCondition.makeCondition("retireDate", EntityOperator.EQUALS, null), EntityOperator.OR,
      EntityCondition.makeCondition("retireDate", EntityOperator.GREATER_THAN_EQUAL_TO, monthStart))
  ]
  if (parameters.partyId) emplConds.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId))
  def empls = from("FrEmplInfo").where(EntityCondition.makeCondition(emplConds, EntityOperator.AND)).queryList()

  int cnt = 0
  for (empl in empls) {
    BigDecimal otOld = BigDecimal.ZERO
    BigDecimal otNew = BigDecimal.ZERO
    BigDecimal holi = BigDecimal.ZERO
    int days = 0

    // 전환기간 임시 - 옛 근태(FrAttendance)도 같이 읽음. 전 직원 새 근태 전환되면 삭제
    def olds = from("FrAttendance").where(EntityCondition.makeCondition([
        EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, empl.partyId),
        EntityCondition.makeCondition("workDate", EntityOperator.GREATER_THAN_EQUAL_TO, monthStart),
        EntityCondition.makeCondition("workDate", EntityOperator.LESS_THAN_EQUAL_TO, monthEnd)
      ], EntityOperator.AND)).queryList()
    for (a in olds) {
      otOld = otOld + (a.overtimeHours ?: 0)
      if (a.workHours && a.workHours > 0) days++
    }

    def news = from("FrAttend2").where(EntityCondition.makeCondition([
        EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, empl.partyId),
        EntityCondition.makeCondition("attDate", EntityOperator.GREATER_THAN_EQUAL_TO, monthStart),
        EntityCondition.makeCondition("attDate", EntityOperator.LESS_THAN_EQUAL_TO, monthEnd)
      ], EntityOperator.AND)).queryList()
    for (a in news) {
      otNew = otNew + (a.otHr ?: 0)
      holi = holi + (a.holiHr ?: 0)
      if (a.workHr && a.workHr > 0) days++
    }

    BigDecimal total = otOld + otNew  // 전환기간 임시
    BigDecimal base = empl.baseSalary ?: BigDecimal.ZERO
    BigDecimal hourly = base.divide(stdHours, 0, RoundingMode.FLOOR)
    BigDecimal pay = (hourly * factor * (total + holi)).setScale(0, RoundingMode.FLOOR)
    println "att2 close >>> " + empl.partyId + " old=" + otOld + " new=" + otNew + " holi=" + holi + " pay=" + pay

    def c = delegator.makeValue("FrAttend2Close")
    c.closeYm = closeYm
    c.partyId = empl.partyId
    c.workDays = days
    c.otHrOld = otOld
    c.otHrNew = otNew
    c.holiHr = holi
    c.otHrTotal = total
    c.hourlyWage = hourly
    c.otPayAmt = pay
    c.closeDate = UtilDateTime.nowTimestamp()
    delegator.createOrStore(c)
    cnt++
  }

  def result = success("근태 마감 " + closeYm + " : " + cnt + "명")
  result.closeCount = cnt
  return result
}
