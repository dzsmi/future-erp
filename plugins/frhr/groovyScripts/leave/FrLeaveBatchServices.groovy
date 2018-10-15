// 작성: 윤서진 / 2018.10.15
/*
 * 연차 일괄 발생 배치 (근로기준법 제60조 개정 2018.05.29 시행분 반영)
 *
 *  - 입사 1년 미만 : 1개월 개근(해당 월 결근 없음)마다 1일, 최대 11일
 *  - 만 1년 이상   : 15일 + 3년차부터 2년마다 1일 가산, 최대 25일
 *                    1년 미만 기간에 사용한 연차는 차감하지 않음 (개정법)
 *  - 발생유형 AUTO, 같은 근속차수로 이미 발생한 건(수동 발생 포함)은 건너뜀
 *  - 스케줄 : data/FrHrLeaveJobData.xml (매월 1일, 매년 1/1)
 *
 *  ※ 기존 수동 발생(grantFrLeave, minilang)은 아직 개정 전 규칙임. 배치로 대체 예정.
 */

import java.sql.Date
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def grantFrLeaveBatch() {
    String module = "FrLeaveBatchServices.groovy"
    LocalDate asOf = parameters.asOfDate ? parameters.asOfDate.toLocalDate() : LocalDate.now()
    Date asOfDate = Date.valueOf(asOf)
    String grantMode = parameters.grantMode ?: "MONTHLY"
    int underYearMax = 11

    List emplConds = [
        EntityCondition.makeCondition("hireDate", EntityOperator.LESS_THAN, asOfDate),
        EntityCondition.makeCondition("retireDate", EntityOperator.EQUALS, null)
    ]
    if (parameters.partyId) {
        emplConds.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId))
    }
    List<GenericValue> emplInfos = from("FrEmplInfo").where(EntityCondition.makeCondition(emplConds, EntityOperator.AND)).orderBy("emplNo").queryList()

    int grantCount = 0
    BigDecimal grantTotalDays = BigDecimal.ZERO
    for (GenericValue emplInfo : emplInfos) {
        LocalDate hire = emplInfo.hireDate.toLocalDate()
        int serviceYears = Period.between(hire, asOf).getYears()
        BigDecimal grantDays = BigDecimal.ZERO
        long serviceYearNo
        String remarks

        if (serviceYears < 1) {
            // 1년 미만 : 지난 달까지 꽉 찬 개월 중 개근월 수 (최대 11)
            serviceYearNo = 0L
            long fullMonths = ChronoUnit.MONTHS.between(hire, asOf)
            int perfectMonths = 0
            for (long i = 0; i < fullMonths; i++) {
                Date monthFrom = Date.valueOf(hire.plusMonths(i))
                Date monthThru = Date.valueOf(hire.plusMonths(i + 1))
                long absentCount = from("FrAttendance").where(EntityCondition.makeCondition([
                        EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, emplInfo.partyId),
                        EntityCondition.makeCondition("attendTypeId", EntityOperator.EQUALS, "FRAT_ABSENT"),
                        EntityCondition.makeCondition("workDate", EntityOperator.GREATER_THAN_EQUAL_TO, monthFrom),
                        EntityCondition.makeCondition("workDate", EntityOperator.LESS_THAN, monthThru)
                    ], EntityOperator.AND)).queryCount()
                if (absentCount == 0) perfectMonths++
            }
            if (perfectMonths > underYearMax) perfectMonths = underYearMax

            BigDecimal alreadyGranted = BigDecimal.ZERO
            from("FrLeaveGrant").where(partyId: emplInfo.partyId, serviceYearNo: 0L).queryList().each {
                alreadyGranted = alreadyGranted.add(it.grantDays ?: BigDecimal.ZERO)
            }
            grantDays = new BigDecimal(perfectMonths).subtract(alreadyGranted)
            remarks = "연차 배치(${grantMode}) 1년 미만 개근 ${perfectMonths}개월"
        } else {
            // 만 1년 이상 : 근속차수별 1회 (1년 미만 사용분 차감 없음)
            serviceYearNo = serviceYears as Long
            GenericValue sameYearGrant = from("FrLeaveGrant").where(partyId: emplInfo.partyId, serviceYearNo: serviceYearNo).queryFirst()
            if (sameYearGrant) {
                continue
            }
            int days = 15 + (int) ((serviceYears - 1) / 2)
            grantDays = new BigDecimal(Math.min(25, days))
            remarks = "연차 배치(${grantMode}) 근속 ${serviceYears}년"
        }

        if (grantDays.compareTo(BigDecimal.ZERO) <= 0) {
            continue
        }

        GenericValue leaveGrant = makeValue("FrLeaveGrant")
        leaveGrant.leaveGrantId = delegator.getNextSeqId("FrLeaveGrant")
        leaveGrant.partyId = emplInfo.partyId
        leaveGrant.grantYear = asOf.getYear() as Long
        leaveGrant.serviceYearNo = serviceYearNo
        leaveGrant.grantType = "AUTO"
        leaveGrant.grantDays = grantDays
        leaveGrant.grantDate = asOfDate
        leaveGrant.remarks = remarks
        leaveGrant.createdByUserLogin = userLogin?.userLoginId
        leaveGrant.create()

        grantCount++
        grantTotalDays = grantTotalDays.add(grantDays)
    }

    Debug.logInfo("연차 배치 완료 asOfDate=${asOfDate}, mode=${grantMode}, 대상 ${emplInfos.size()}명, 발생 ${grantCount}건 / ${grantTotalDays}일", module)
    Map result = success(UtilProperties.getMessage("FrHrUiLabels", "FrHrLeaveBatchSuccess", [grantCount, grantTotalDays] as Object[], locale))
    result.grantCount = grantCount as Long
    return result
}
