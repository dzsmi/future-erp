// 작성: 오픈브릿지시스템즈 이소라 / 2018.04.09
/*
 * 재경 전표 → OFBiz 회계 거래(AcctgTrans) 생성
 * - approveFrSlip(minilang) 에서 최종 결재레벨 승인 시 호출
 * - 코어 createAcctgTrans / createAcctgTransEntry 사용, 전기(post)는 회계모듈에서 별도 처리
 */

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtilProperties

def postFrSlipAcctgTrans() {
    Debug.logInfo("===== postFrSlipAcctgTrans =====", "")
    GenericValue slip = from('FrSlip').where('slipId', parameters.slipId).queryOne()
    if (!slip) {
        return error(UtilProperties.getMessage('FrAcctErrorUiLabels', 'FrAcctSlipNotFound', parameters.locale))
    }
    if (slip.statusId != 'FRSLIP_APPROVED') {
        return error(UtilProperties.getMessage('FrAcctErrorUiLabels', 'FrAcctSlipNotApproved', parameters.locale))
    }
    if (slip.acctgTransId) {
        return error(UtilProperties.getMessage('FrAcctErrorUiLabels', 'FrAcctSlipAlreadyPosted', parameters.locale))
    }

    String organizationPartyId = EntityUtilProperties.getPropertyValue('fracct', 'fracct.organizationPartyId', 'FR_COMPANY', delegator)
    String acctgTransTypeId = EntityUtilProperties.getPropertyValue('fracct', 'fracct.acctgTransTypeId', 'INTERNAL_ACCTG_TRANS', delegator)
    String glFiscalTypeId = EntityUtilProperties.getPropertyValue('fracct', 'fracct.glFiscalTypeId', 'ACTUAL', delegator)
    String acctgTransEntryTypeId = EntityUtilProperties.getPropertyValue('fracct', 'fracct.acctgTransEntryTypeId', '_NA_', delegator)
    String currencyUomId = EntityUtilProperties.getPropertyValue('fracct', 'fracct.currencyUomId', 'KRW', delegator)

    // 1. 회계 거래 헤더
    Map transCtx = [acctgTransTypeId: acctgTransTypeId,
                    glFiscalTypeId: glFiscalTypeId,
                    transactionDate: UtilDateTime.toTimestamp(slip.slipDate),
                    description: '[' + slip.slipId + '] ' + (slip.description ?: '')]
    Map transResult = run service: 'createAcctgTrans', with: transCtx
    String acctgTransId = transResult.acctgTransId

    // 2. 회계 거래 항목 (전표 항목 1:1)
    List slipItems = from('FrSlipItem').where('slipId', slip.slipId).orderBy('slipItemSeqId').queryList()
    for (GenericValue slipItem : slipItems) {
        Map entryCtx = [acctgTransId: acctgTransId,
                        acctgTransEntryTypeId: acctgTransEntryTypeId,
                        organizationPartyId: organizationPartyId,
                        glAccountId: slipItem.glAccountId,
                        debitCreditFlag: slipItem.debitCreditFlag,
                        amount: slipItem.amount,
                        origAmount: slipItem.amount,
                        currencyUomId: currencyUomId,
                        origCurrencyUomId: currencyUomId,
                        partyId: slipItem.partyId,
                        voucherRef: slip.slipId,
                        description: slipItem.comments]
        run service: 'createAcctgTransEntry', with: entryCtx
    }

    // 3. 전표에 회계 거래 연결
    slip.acctgTransId = acctgTransId
    slip.lastModifiedByUserLogin = userLogin.userLoginId
    slip.store()

    Map result = success()
    result.acctgTransId = acctgTransId
    return result
}
