/*
 * 퇴직금 정산 (2025.02 조민석 [SR-2025-0142])
 *
 *  평균임금(1일) = 직전 3개월 임금총액 / 직전 3개월 역일수
 *  퇴직금 = 평균임금 x 30 x 재직일수 / 365 (원 미만 절사)
 *  - 직전 3개월 : 퇴사월 이전 3개 급여월 (FrPayroll.payYearMonth)
 *  - 임금총액 : 기본급 지급액 + 연장수당
 *  - 상여 제외
 *  - 재직 1년 미만 0원
 *  - 퇴사일 없으면 FrEmplInfo.retireDate, 그것도 없으면 오늘 기준 (예상 퇴직금)
 */

import java.math.RoundingMode
import java.sql.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def calcFrSeverancePay() {
    GenericValue emplInfo = from("FrEmplInfo").where(partyId: parameters.partyId).queryOne()
    if (!emplInfo) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorEmplInfoNotFound", locale))
    }

    Date retireDate = parameters.retireDate
    if (!retireDate) {
        retireDate = emplInfo.retireDate
    }
    if (!retireDate) {
        retireDate = UtilDateTime.nowDate()
    }
    Date hireDate = emplInfo.hireDate
    if (retireDate.before(hireDate)) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorRetireBeforeHire", locale))
    }

    long serviceDays = ChronoUnit.DAYS.between(hireDate.toLocalDate(), retireDate.toLocalDate())

    // 직전 3개월 (퇴사월 제외)
    DateTimeFormatter ymFmt = DateTimeFormatter.ofPattern("yyyyMM")
    LocalDate retireMonthStart = retireDate.toLocalDate().withDayOfMonth(1)
    LocalDate fromMonth = retireMonthStart.minusMonths(3)
    String fromYearMonth = fromMonth.format(ymFmt)
    String thruYearMonth = retireMonthStart.minusMonths(1).format(ymFmt)
    long calDays3Month = ChronoUnit.DAYS.between(fromMonth, retireMonthStart)

    List conds = [
        EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId),
        EntityCondition.makeCondition("payYearMonth", EntityOperator.GREATER_THAN_EQUAL_TO, fromYearMonth),
        EntityCondition.makeCondition("payYearMonth", EntityOperator.LESS_THAN_EQUAL_TO, thruYearMonth)
    ]
    List<GenericValue> payrolls = from("FrPayroll").where(EntityCondition.makeCondition(conds, EntityOperator.AND)).orderBy("payYearMonth").queryList()

    BigDecimal wage3MonthAmount = BigDecimal.ZERO
    List payrollRows = []
    for (GenericValue payroll : payrolls) {
        BigDecimal baseAmount = payroll.baseAmount ?: BigDecimal.ZERO
        BigDecimal overtimeAmount = payroll.overtimeAmount ?: BigDecimal.ZERO
        // 상여는 평균임금에 넣지 않음
        wage3MonthAmount = wage3MonthAmount + baseAmount + overtimeAmount
        payrollRows.add([payYearMonth: payroll.payYearMonth, baseAmount: baseAmount, overtimeAmount: overtimeAmount, wageAmount: baseAmount + overtimeAmount])
    }

    BigDecimal avgDailyWage = BigDecimal.ZERO
    if (calDays3Month > 0) {
        avgDailyWage = wage3MonthAmount.divide(new BigDecimal(calDays3Month), 2, RoundingMode.HALF_UP)
    }

    BigDecimal retirePayAmount = BigDecimal.ZERO
    if (serviceDays >= 365) {
        retirePayAmount = (avgDailyWage * 30 * serviceDays).divide(new BigDecimal("365"), 0, RoundingMode.FLOOR)
    }

    logInfo("퇴직금 정산 partyId=${parameters.partyId} 퇴사일=${retireDate} 재직일수=${serviceDays} 3개월임금=${wage3MonthAmount} 역일수=${calDays3Month} 평균임금=${avgDailyWage} 퇴직금=${retirePayAmount}")

    Map result = success()
    result.hireDate = hireDate
    result.retireDate = retireDate
    result.serviceDays = serviceDays
    result.wage3MonthAmount = wage3MonthAmount
    result.calDays3Month = calDays3Month
    result.avgDailyWage = avgDailyWage
    result.retirePayAmount = retirePayAmount
    result.payrollRows = payrollRows
    return result
}
