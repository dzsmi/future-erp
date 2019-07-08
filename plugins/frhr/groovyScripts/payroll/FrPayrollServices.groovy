// 작성: 오픈브릿지시스템즈 이소라 / 2018.04.16
/*
 * (주)퓨처리테일 월 급여 일괄 계산
 *
 *  - 대상 : 급여월 말일 이전 입사, 퇴사일 없는 직원 (퇴사자는 퇴사 처리의 마지막 달 정산으로 지급)
 *  - 기본급 : 입사월이면 calcDailyProrate(기본급 x 근무일수 / 30) 로 일할계산
 *            → 2019.07 윤서진 : 해당 월 역일 기준(기본급 x 근무일수 / 그 달 일수)으로 변경
 *  - 연장수당 : 해당월 근태 연장시간 합계 x 통상시급(기본급/209) x 1.5
 *  - 상여 : 화면에서 입력한 상여율(%) x 기본급
 *  - 4대보험 : 건강/장기요양/고용 요율은 FrInsuranceRate(급여년도), 국민연금 4.5%
 *  - 소득세 : 간이세액표 반영 전까지 지급총액의 3%
 *  - 이미 확정/지급된 급여는 다시 계산하지 않는다
 */

import java.math.RoundingMode
import java.sql.Date
import java.time.YearMonth

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def calcFrMonthlyPayroll() {
    String payYearMonth = parameters.payYearMonth
    if (!payYearMonth || !(payYearMonth ==~ /\d{6}/)) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorPayYearMonth", locale))
    }
    YearMonth ym = YearMonth.of(payYearMonth.substring(0, 4) as int, payYearMonth.substring(4, 6) as int)
    Date monthStart = Date.valueOf(ym.atDay(1))
    Date monthEnd = Date.valueOf(ym.atEndOfMonth())
    Long payYear = ym.getYear() as Long

    // 요율 (%)
    BigDecimal healthRate = getInsuranceRate(payYear, "HEALTH")
    BigDecimal longtermRate = getInsuranceRate(payYear, "LONGTERM")
    BigDecimal employRate = getInsuranceRate(payYear, "EMPLOY")
    if (healthRate == null || longtermRate == null || employRate == null) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorInsuranceRateNotFound", locale))
    }
    BigDecimal pensionRate = new BigDecimal("4.5")
    BigDecimal incomeTaxRate = new BigDecimal("3")

    BigDecimal standardHours = new BigDecimal(UtilProperties.getPropertyValue("frhr", "payroll.standardMonthlyHours", "209"))
    BigDecimal overtimeFactor = new BigDecimal(UtilProperties.getPropertyValue("frhr", "payroll.overtimeFactor", "1.5"))
    BigDecimal bonusPercent = parameters.bonusPercent ?: BigDecimal.ZERO

    List emplConds = [
        EntityCondition.makeCondition("hireDate", EntityOperator.LESS_THAN_EQUAL_TO, monthEnd),
        EntityCondition.makeCondition("retireDate", EntityOperator.EQUALS, null)
    ]
    List<GenericValue> emplInfos = from("FrEmplInfo").where(EntityCondition.makeCondition(emplConds, EntityOperator.AND)).orderBy("emplNo").queryList()

    int payrollCount = 0
    int skippedCount = 0
    for (GenericValue emplInfo : emplInfos) {
        GenericValue payroll = from("FrPayroll").where(payYearMonth: payYearMonth, partyId: emplInfo.partyId).queryFirst()
        if (payroll && payroll.statusId != "FRPAY_DRAFT") {
            skippedCount++
            continue
        }

        BigDecimal baseSalary = emplInfo.baseSalary ?: BigDecimal.ZERO
        BigDecimal baseAmount = baseSalary
        Long workDays = null

        // 중도입사 : 입사일 ~ 말일 일할계산
        // 2019.07.08 윤서진 : 30일 기준 → 해당 월 역일 기준 (노무팀 요청, 31일 달 입사자 과지급 문제)
        if (emplInfo.hireDate.after(monthStart)) {
            workDays = (monthEnd - emplInfo.hireDate) + 1
            // Map prorateResult = runService("calcDailyProrate", [baseSalary: baseSalary, workDays: workDays])
            // baseAmount = prorateResult.prorateAmount
            BigDecimal monthDays = new BigDecimal(ym.lengthOfMonth())
            baseAmount = (baseSalary * workDays).divide(monthDays, 0, RoundingMode.FLOOR)
        }

        // 연장수당
        List attendConds = [
            EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, emplInfo.partyId),
            EntityCondition.makeCondition("workDate", EntityOperator.GREATER_THAN_EQUAL_TO, monthStart),
            EntityCondition.makeCondition("workDate", EntityOperator.LESS_THAN_EQUAL_TO, monthEnd)
        ]
        List<GenericValue> attendances = from("FrAttendance").where(EntityCondition.makeCondition(attendConds, EntityOperator.AND)).queryList()
        BigDecimal overtimeHours = BigDecimal.ZERO
        for (GenericValue attendance : attendances) {
            if (attendance.overtimeHours) {
                overtimeHours = overtimeHours.add(attendance.overtimeHours)
            }
        }
        BigDecimal hourlyWage = baseSalary.divide(standardHours, 0, RoundingMode.FLOOR)
        BigDecimal overtimeAmount = (hourlyWage * overtimeFactor * overtimeHours).setScale(0, RoundingMode.FLOOR)

        // 상여
        BigDecimal bonusAmount = calcPercentAmount(baseSalary, bonusPercent)

        BigDecimal grossAmount = baseAmount + overtimeAmount + bonusAmount

        // 공제
        BigDecimal pensionAmount = calcPercentAmount(grossAmount, pensionRate)
        BigDecimal healthAmount = calcPercentAmount(grossAmount, healthRate)
        BigDecimal longtermAmount = calcPercentAmount(healthAmount, longtermRate)
        BigDecimal employAmount = calcPercentAmount(grossAmount, employRate)
        BigDecimal incomeTaxAmount = calcPercentAmount(grossAmount, incomeTaxRate)
        BigDecimal deductionAmount = pensionAmount + healthAmount + longtermAmount + employAmount + incomeTaxAmount
        BigDecimal netAmount = grossAmount - deductionAmount

        if (!payroll) {
            payroll = delegator.makeValue("FrPayroll")
            payroll.payrollId = delegator.getNextSeqId("FrPayroll")
            payroll.payYearMonth = payYearMonth
            payroll.partyId = emplInfo.partyId
        }
        payroll.baseSalary = baseSalary
        payroll.workDays = workDays
        payroll.baseAmount = baseAmount
        payroll.overtimeHours = overtimeHours
        payroll.overtimeAmount = overtimeAmount
        payroll.bonusAmount = bonusAmount
        payroll.grossAmount = grossAmount
        payroll.pensionAmount = pensionAmount
        payroll.healthAmount = healthAmount
        payroll.longtermAmount = longtermAmount
        payroll.employAmount = employAmount
        payroll.incomeTaxAmount = incomeTaxAmount
        payroll.deductionAmount = deductionAmount
        payroll.netAmount = netAmount
        payroll.statusId = "FRPAY_DRAFT"
        payroll.calcDate = UtilDateTime.nowTimestamp()
        payroll.calcByUserLogin = userLogin?.userLoginId
        delegator.createOrStore(payroll)
        payrollCount++
    }

    logInfo("급여 일괄계산 ${payYearMonth} : 계산 ${payrollCount}건, 확정/지급 건너뜀 ${skippedCount}건")
    Map result = success(UtilProperties.getMessage("FrHrUiLabels", "FrHrPayrollCalcSuccess", [payrollCount, skippedCount] as Object[], locale))
    result.payrollCount = payrollCount
    result.skippedCount = skippedCount
    return result
}

/**
 * FrInsuranceRate 에서 해당년도 요율(%) 조회
 */
def getInsuranceRate(Long rateYear, String insuranceType) {
    GenericValue rateValue = from("FrInsuranceRate").where(rateYear: rateYear, insuranceType: insuranceType).cache().queryOne()
    return rateValue?.rate
}

/**
 * 금액 x 요율(%) / 100, 원 미만 절사
 */
def calcPercentAmount(BigDecimal amount, BigDecimal ratePercent) {
    if (!amount || !ratePercent) {
        return BigDecimal.ZERO
    }
    return amount.multiply(ratePercent).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
}
