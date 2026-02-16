// 작성: 한예린 / 2023.09.11
/*
 * [SR-2023-1502] 결재선 기준 데이터화 (재무팀 요청)
 *  - 결재선 기준금액을 하드코딩 대신 FrApprLine(전표유형별) 데이터로 관리 -> 관리 화면 FindFrApprLine
 *  - 전표유형의 유효한(fromDate/thruDate) 기준을 sequenceNum 순으로 보고
 *    전표금액 <= maxAmount 인 첫 번째 결재레벨 (maxAmount 비어 있으면 상한 없음)
 *  - 기준 데이터가 없는 전표유형은 기존 getFrApprLevel(minilang determineApprLevel) 결과 그대로
 *  - 1차 적용 : 경비전표(FRSLIP_EXPENSE) 팀장 500만 / 본부장 5000만
 */
// 일반전표 전환은 2024 예정 (입금/출금/대체전표는 FrSlipServices.xml#determineApprLevel 유지)

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue

def determineFrApprLevelByData() {
    println "########## determineFrApprLevelByData start"
    String module = "FrApprLineServices.groovy"
    BigDecimal amount = parameters.amount ?: BigDecimal.ZERO
    String slipTypeEnumId = parameters.slipTypeEnumId

    List<GenericValue> apprLines = []
    if (slipTypeEnumId) {
        apprLines = from("FrApprLine")
                .where(slipTypeEnumId: slipTypeEnumId)
                .orderBy("sequenceNum")
                .filterByDate(UtilDateTime.nowTimestamp())
                .queryList()
    }

    Map result = success()
    if (!apprLines) {
        // 기준 데이터 없는 전표유형 -> 기존 결재선
        Debug.logInfo("결재선 기준 데이터 없음 slipTypeEnumId=${slipTypeEnumId}, 기존 결재선(getFrApprLevel) 사용", module)
        Map levelResult = runService("getFrApprLevel", [amount: amount])
        result.apprLevelEnumId = levelResult.apprLevelEnumId
        return result
    }

    GenericValue matched = null
    for (GenericValue apprLine : apprLines) {
        if (apprLine.maxAmount == null || amount.compareTo(apprLine.maxAmount) <= 0) {
            matched = apprLine
            break
        }
    }
    if (matched == null) {
        // 상한 없는 행이 없으면 마지막 행(가장 높은 레벨)
        matched = apprLines.get(apprLines.size() - 1)
    }
    result.apprLevelEnumId = matched.apprLevelEnumId
    result.apprLineId = matched.apprLineId
    return result
}
