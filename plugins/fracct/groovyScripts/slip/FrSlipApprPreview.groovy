// 작성: 정해인 / 2020.04.20
/*
 * 전표 작성 화면(EditFrSlipItems) 예상 결재선 미리보기
 *  - 미리보기 금액 : 화면에서 입력한 금액(previewAmount), 없으면 현재 차변 합계(FrSlipBalance.groovy 결과)
 *  - 2020.04 재무팀 결재 기준 (재무팀 공지 2020-04)
 *      5,000,000원 이하   : 팀장 전결
 *      50,000,000원 이하  : 팀장 → 본부장
 *      50,000,000원 초과  : 팀장 → 본부장 → 대표이사
 *  - 표시용. 저장하지 않음.
 */

import org.apache.ofbiz.base.util.UtilFormatOut
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.base.util.UtilValidate

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

BigDecimal teamLimit = new BigDecimal("5000000")
BigDecimal divLimit = new BigDecimal("50000000")

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
