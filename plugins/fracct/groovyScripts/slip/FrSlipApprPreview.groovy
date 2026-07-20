// 작성: 정해인 / 2020.04.20
/*
 * 전표 작성 화면(EditFrSlipItems) 예상 결재선 미리보기
 *  - 미리보기 금액 : 화면에서 입력한 금액(previewAmount), 없으면 현재 차변 합계(FrSlipBalance.groovy 결과)
 *  - 2020.04 재무팀 결재 기준 (재무팀 공지 2020-04)
 *      5,000,000원 이하   : 팀장 전결
 *      50,000,000원 이하  : 팀장 → 본부장
 *      50,000,000원 초과  : 팀장 → 본부장 → 대표이사
 *  - 표시용. 저장하지 않음.
 *  - 2026.07.20 조민석 [SR-2026-0688] 미리보기/실제 결재선 다름 VOC
 *      하드코딩 대신 결재선 기준 데이터(FrApprLine, 결재선 기준 관리 화면) 조회로 변경
 *      전표유형 기준 데이터 없으면 경비전표(FRSLIP_EXPENSE) 기준 사용
 */

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilFormatOut
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.base.util.UtilValidate

println "########## FrSlipApprPreview.groovy start"
BigDecimal previewAmount = null
if (UtilValidate.isNotEmpty(parameters.previewAmount)) {
    try {
        previewAmount = new BigDecimal(parameters.previewAmount.toString().replace(",", "").trim())
    } catch (NumberFormatException e) {
        previewAmount = null
    }
}
if (previewAmount == null) {
    previewAmount = context.debitTotal ?: BigDecimal.ZERO
}

String team = UtilProperties.getMessage("FrAcctUiLabels", "FrAcctApprTeam", locale)
String div = UtilProperties.getMessage("FrAcctUiLabels", "FrAcctApprDiv", locale)
String ceo = UtilProperties.getMessage("FrAcctUiLabels", "FrAcctApprCeo", locale)

// BigDecimal teamLimit = new BigDecimal("5000000")
// BigDecimal divLimit = new BigDecimal("50000000")
BigDecimal teamLimit = null
BigDecimal divLimit = null

def slip = from("FrSlip").where(slipId: parameters.slipId).queryOne()
String slipTypeEnumId = slip?.slipTypeEnumId
List apprLines = []
if (slipTypeEnumId) {
    apprLines = from("FrApprLine").where(slipTypeEnumId: slipTypeEnumId).orderBy("sequenceNum").filterByDate(UtilDateTime.nowTimestamp()).queryList()
}
if (!apprLines) {
    // 결재선 기준 데이터가 경비전표만 있음
    apprLines = from("FrApprLine").where(slipTypeEnumId: "FRSLIP_EXPENSE").orderBy("sequenceNum").filterByDate(UtilDateTime.nowTimestamp()).queryList()
}
apprLines.each { apprLine ->
    if ("FRAPPR_TEAM" == apprLine.apprLevelEnumId && apprLine.maxAmount != null) {
        teamLimit = apprLine.maxAmount
    } else if ("FRAPPR_DIV" == apprLine.apprLevelEnumId && apprLine.maxAmount != null) {
        divLimit = apprLine.maxAmount
    }
}
if (teamLimit == null) {
    teamLimit = new BigDecimal("5000000")
}
if (divLimit == null) {
    divLimit = new BigDecimal("50000000")
}
println "########## FrSlipApprPreview slipType=" + slipTypeEnumId + " team=" + teamLimit + " div=" + divLimit

String previewLevelEnumId
List previewSteps = []
if (previewAmount.compareTo(teamLimit) <= 0) {
    previewLevelEnumId = "FRAPPR_TEAM"
    previewSteps = [team]
} else if (previewAmount.compareTo(divLimit) <= 0) {
    previewLevelEnumId = "FRAPPR_DIV"
    previewSteps = [team, div]
} else {
    previewLevelEnumId = "FRAPPR_CEO"
    previewSteps = [team, div, ceo]
}

context.previewAmount = previewAmount
context.previewAmountText = UtilFormatOut.formatAmount(previewAmount.doubleValue())
context.previewLevelEnumId = previewLevelEnumId
context.previewApprLine = previewSteps.join(" → ")
context.previewTeamLimit = teamLimit
context.previewDivLimit = divLimit
