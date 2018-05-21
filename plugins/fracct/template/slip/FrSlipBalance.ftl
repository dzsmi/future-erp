<#-- 작성: 오픈브릿지시스템즈 이소라 / 2018.05.21 -->
<#-- 전표 차변/대변 합계 및 일치 여부 (FrSlipBalance.groovy) -->
<table class="basic-table" cellspacing="0">
  <tr class="header-row">
    <td>${uiLabelMap.FrAcctDebitTotal}</td>
    <td>${uiLabelMap.FrAcctCreditTotal}</td>
    <td>${uiLabelMap.FrAcctBalanceDiff}</td>
    <td>${uiLabelMap.FrAcctBalanceCheck}</td>
  </tr>
  <tr>
    <td><@ofbizCurrency amount=debitTotal!0 isoCode="KRW"/></td>
    <td><@ofbizCurrency amount=creditTotal!0 isoCode="KRW"/></td>
    <td><@ofbizCurrency amount=balanceDiff!0 isoCode="KRW"/></td>
    <td>
      <#if (isBalanced!"N") == "Y">
        <span>${uiLabelMap.FrAcctBalanced}</span>
      <#else>
        <span class="alert">${uiLabelMap.FrAcctNotBalanced}</span>
      </#if>
    </td>
  </tr>
</table>
