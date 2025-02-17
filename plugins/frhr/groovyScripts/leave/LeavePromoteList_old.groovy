// 2025.02.17 한예린 백업
// 작성: 윤서진 / 2020.11.23
/*
 * 연차 사용촉진 대상 조회 (근로기준법 제61조)
 *
 *  - 기준년도(grantYear) 1/1 ~ 12/31 회계연도 기준
 *  - 잔여연차 = 연차 배치(grantFrLeaveBatch) 발생일수 - 승인된 사용일수
 *      발생 : FrLeaveGrant grantYear = 기준년도, grantType = AUTO
 *      사용 : FrLeaveUse 기준년도 시작분, statusId = FRLU_APPROVED
 *  - 잔여 minRemainDays(기본 1일) 이상인 재직자만 대상
 *  - 1차 촉진 : 사용기간 종료 6개월 전 10일 이내 (7/1 ~ 7/10) 잔여일수 통보·사용시기 지정 요청
 *    2차 촉진 : 사용기간 종료 2개월 전 (10/31) 까지 사용시기 지정 통보
 */

import java.sql.Date
import java.time.LocalDate

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

Long grantYear = parameters.grantYear ? (parameters.grantYear as Long) : (LocalDate.now().getYear() as Long)
BigDecimal minRemainDays = parameters.minRemainDays ? new BigDecimal(parameters.minRemainDays.toString()) : BigDecimal.ONE
context.grantYear = grantYear
context.minRemainDays = minRemainDays

Date yearStart = Date.valueOf(LocalDate.of(grantYear.intValue(), 1, 1))
Date yearEnd = Date.valueOf(LocalDate.of(grantYear.intValue(), 12, 31))
context.promote1From = Date.valueOf(LocalDate.of(grantYear.intValue(), 7, 1))
context.promote1Thru = Date.valueOf(LocalDate.of(grantYear.intValue(), 7, 10))
context.promote2Thru = Date.valueOf(LocalDate.of(grantYear.intValue(), 10, 31))

List conds = [
    EntityCondition.makeCondition("hireDate", EntityOperator.LESS_THAN_EQUAL_TO, yearEnd),
    EntityCondition.makeCondition("retireDate", EntityOperator.EQUALS, null)
]
if (parameters.partyId) {
    conds.add(EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, parameters.partyId))
}
List<GenericValue> emplInfos = from("FrEmplInfo").where(EntityCondition.makeCondition(conds, EntityOperator.AND)).orderBy("emplNo").queryList()

List promoteList = []
for (GenericValue emplInfo : emplInfos) {
    // 배치 발생분
    BigDecimal grantDays = BigDecimal.ZERO
    from("FrLeaveGrant").where(partyId: emplInfo.partyId, grantYear: grantYear, grantType: "AUTO").queryList().each {
        grantDays = grantDays.add(it.grantDays ?: BigDecimal.ZERO)
    }

    // 승인된 사용분
    BigDecimal usedDays = BigDecimal.ZERO
    from("FrLeaveUse").where(EntityCondition.makeCondition([
            EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, emplInfo.partyId),
            EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "FRLU_APPROVED"),
            EntityCondition.makeCondition("fromDate", EntityOperator.GREATER_THAN_EQUAL_TO, yearStart),
            EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN_EQUAL_TO, yearEnd)
        ], EntityOperator.AND)).queryList().each {
        usedDays = usedDays.add(it.useDays ?: BigDecimal.ZERO)
    }

    BigDecimal remainDays = grantDays.subtract(usedDays)
    if (remainDays.compareTo(minRemainDays) < 0) {
        continue
    }
    promoteList.add([partyId   : emplInfo.partyId,
                     emplNo    : emplInfo.emplNo,
                     hireDate  : emplInfo.hireDate,
                     departmentId: emplInfo.departmentId,
                     grantDays : grantDays,
                     usedDays  : usedDays,
                     remainDays: remainDays])
}
context.promoteList = promoteList
context.promoteCount = promoteList.size()
