// 작성: 오픈브릿지시스템즈 이소라 / 2018.08.06
/*
 * 급여 미리보기 화면 데이터 준비 (저장하지 않음)
 *  - 화면 응답 속도 때문에 4대보험 요율은 DB 조회 대신 frhr.properties 값을 사용
 *  - 미리보기는 월 기본급 기준 (일할계산 미반영)
 */

import java.math.RoundingMode
import java.sql.Date
import java.time.YearMonth

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

payYearMonth = parameters.payYearMonth ?: UtilDateTime.nowDateString("yyyyMM")
context.payYearMonth = payYearMonth
partyId = parameters.partyId

if (partyId && payYearMonth ==~ /\d{6}/) {
    GenericValue emplInfo = from("FrEmplInfo").where(partyId: partyId).queryOne()
    if (emplInfo) {
        context.emplInfo = emplInfo
        context.person = from("Person").where(partyId: partyId).queryOne()

        BigDecimal healthRate = new BigDecimal(UtilProperties.getPropertyValue("frhr", "health.rate", "3.12"))
        BigDecimal longtermRate = new BigDecimal(UtilProperties.getPropertyValue("frhr", "longterm.rate", "7.38"))
        BigDecimal pensionRate = new BigDecimal(UtilProperties.getPropertyValue("frhr", "pension.rate", "4.5"))
        BigDecimal employRate = new BigDecimal(UtilProperties.getPropertyValue("frhr", "employ.rate", "0.65"))
        BigDecimal incomeTaxRate = new BigDecimal(UtilProperties.getPropertyValue("frhr", "incomeTax.rate", "3"))
        BigDecimal standardHours = new BigDecimal(UtilProperties.getPropertyValue("frhr", "payroll.standardMonthlyHours", "209"))
        BigDecimal overtimeFactor = new BigDecimal(UtilProperties.getPropertyValue("frhr", "payroll.overtimeFactor", "1.5"))
        BigDecimal bonusPercent = parameters.bonusPercent ? new BigDecimal(parameters.bonusPercent) : BigDecimal.ZERO

        // 2022.04.25 임재훈 - 미리보기/명세서 건강보험료 다름 VOC (인사팀)
        // 임시조치 - 2022년부터는 요율 테이블(FrInsuranceRate) 값 사용, 이전 연도는 기존대로 properties
        // TODO 원인 파악 필요 (재현안됨) - properties 요율이 언제부터 안 맞았는지 확인 못함, 과거 연도는 건드리지 않음
        int previewYear = payYearMonth.substring(0, 4) as int
        if (previewYear >= 2022) {
            GenericValue healthRateValue = from("FrInsuranceRate").where(rateYear: (long) previewYear, insuranceType: "HEALTH").queryOne()
            GenericValue longtermRateValue = from("FrInsuranceRate").where(rateYear: (long) previewYear, insuranceType: "LONGTERM").queryOne()
            GenericValue employRateValue = from("FrInsuranceRate").where(rateYear: (long) previewYear, insuranceType: "EMPLOY").queryOne()
            if (healthRateValue?.rate) healthRate = healthRateValue.rate
            if (longtermRateValue?.rate) longtermRate = longtermRateValue.rate
            if (employRateValue?.rate) employRate = employRateValue.rate
            println "### PayrollPreview " + previewYear + " 요율 테이블 사용 health=" + healthRate + " longterm=" + longtermRate + " employ=" + employRate
        }

        YearMonth ym = YearMonth.of(payYearMonth.substring(0, 4) as int, payYearMonth.substring(4, 6) as int)
        Date monthStart = Date.valueOf(ym.atDay(1))
        Date monthEnd = Date.valueOf(ym.atEndOfMonth())

        BigDecimal baseSalary = emplInfo.baseSalary ?: BigDecimal.ZERO

        List attendConds = [
            EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, partyId),
            EntityCondition.makeCondition("workDate", EntityOperator.GREATER_THAN_EQUAL_TO, monthStart),
            EntityCondition.makeCondition("workDate", EntityOperator.LESS_THAN_EQUAL_TO, monthEnd)
        ]
        BigDecimal overtimeHours = BigDecimal.ZERO
        from("FrAttendance").where(EntityCondition.makeCondition(attendConds, EntityOperator.AND)).queryList().each { attendance ->
            if (attendance.overtimeHours) {
                overtimeHours = overtimeHours.add(attendance.overtimeHours)
            }
        }
        BigDecimal hourlyWage = baseSalary.divide(standardHours, 0, RoundingMode.FLOOR)
        BigDecimal overtimeAmount = (hourlyWage * overtimeFactor * overtimeHours).setScale(0, RoundingMode.FLOOR)
        BigDecimal bonusAmount = (baseSalary * bonusPercent).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
        BigDecimal grossAmount = baseSalary + overtimeAmount + bonusAmount

        BigDecimal pensionAmount = (grossAmount * pensionRate).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
        BigDecimal healthAmount = (grossAmount * healthRate).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
        BigDecimal longtermAmount = (healthAmount * longtermRate).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
        BigDecimal employAmount = (grossAmount * employRate).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
        BigDecimal incomeTaxAmount = (grossAmount * incomeTaxRate).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
        BigDecimal deductionAmount = pensionAmount + healthAmount + longtermAmount + employAmount + incomeTaxAmount

        context.preview = [
            baseSalary      : baseSalary,
            overtimeHours   : overtimeHours,
            overtimeAmount  : overtimeAmount,
            bonusAmount     : bonusAmount,
            grossAmount     : grossAmount,
            pensionAmount   : pensionAmount,
            healthAmount    : healthAmount,
            longtermAmount  : longtermAmount,
            employAmount    : employAmount,
            incomeTaxAmount : incomeTaxAmount,
            deductionAmount : deductionAmount,
            netAmount       : grossAmount - deductionAmount
        ]
        context.previewRates = [health: healthRate, longterm: longtermRate, pension: pensionRate, employ: employRate, incomeTax: incomeTaxRate]
    }
}
