// 작성: 오픈브릿지시스템즈 이소라 / 2018.05.21
/*
 * 전표 화면(EditFrSlipItems, ViewFrSlip) 차대 합계 표시용 데이터 준비
 * 합계 계산은 checkFrSlipBalance 서비스(minilang)를 그대로 사용한다.
 */

import org.apache.ofbiz.service.ServiceUtil

println "########## FrSlipBalance.groovy start"
slipId = parameters.slipId ?: context.slipId
if (slipId) {
    Map balance = dispatcher.runSync('checkFrSlipBalance', [slipId: slipId, userLogin: userLogin])
    if (ServiceUtil.isSuccess(balance)) {
        BigDecimal debitTotal = balance.debitTotal ?: BigDecimal.ZERO
        BigDecimal creditTotal = balance.creditTotal ?: BigDecimal.ZERO
        context.debitTotal = debitTotal
        context.creditTotal = creditTotal
        context.balanceDiff = debitTotal.subtract(creditTotal)
        context.isBalanced = balance.isBalanced
    }
}
