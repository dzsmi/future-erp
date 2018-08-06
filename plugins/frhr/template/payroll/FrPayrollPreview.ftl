<#-- 작성: 오픈브릿지시스템즈 이소라 / 2018.08.06 -->
<#-- 급여 미리보기 결과 (저장되지 않음) -->
<#if preview?has_content>
<table class="basic-table" cellspacing="0">
  <tr>
    <td class="label">${uiLabelMap.FrHrEmployee}</td>
    <td>${(emplInfo.emplNo)!} ${(person.lastName)!}${(person.firstName)!}</td>
    <td class="label">${uiLabelMap.FrHrPayYearMonth}</td>
    <td>${payYearMonth!}</td>
  </tr>
  <tr class="header-row">
    <td colspan="2">${uiLabelMap.FrHrPayItems}</td>
    <td colspan="2">${uiLabelMap.FrHrDeductionItems}</td>
  </tr>
  <tr>
    <td>${uiLabelMap.FrHrBaseSalary}</td><td align="right"><@ofbizCurrency amount=preview.baseSalary isoCode="KRW"/></td>
    <td>${uiLabelMap.FrHrPensionAmount} (${previewRates.pension}%)</td><td align="right"><@ofbizCurrency amount=preview.pensionAmount isoCode="KRW"/></td>
  </tr>
  <tr>
    <td>${uiLabelMap.FrHrOvertimeAmount} (${preview.overtimeHours}h)</td><td align="right"><@ofbizCurrency amount=preview.overtimeAmount isoCode="KRW"/></td>
    <td>${uiLabelMap.FrHrHealthAmount} (${previewRates.health}%)</td><td align="right"><@ofbizCurrency amount=preview.healthAmount isoCode="KRW"/></td>
  </tr>
  <tr>
    <td>${uiLabelMap.FrHrBonusAmount}</td><td align="right"><@ofbizCurrency amount=preview.bonusAmount isoCode="KRW"/></td>
    <td>${uiLabelMap.FrHrLongtermAmount} (${previewRates.longterm}%)</td><td align="right"><@ofbizCurrency amount=preview.longtermAmount isoCode="KRW"/></td>
  </tr>
  <tr>
    <td></td><td></td>
    <td>${uiLabelMap.FrHrEmployAmount} (${previewRates.employ}%)</td><td align="right"><@ofbizCurrency amount=preview.employAmount isoCode="KRW"/></td>
  </tr>
  <tr>
    <td></td><td></td>
    <td>${uiLabelMap.FrHrIncomeTaxAmount} (${previewRates.incomeTax}%)</td><td align="right"><@ofbizCurrency amount=preview.incomeTaxAmount isoCode="KRW"/></td>
  </tr>
  <tr class="header-row">
    <td>${uiLabelMap.FrHrGrossAmount}</td><td align="right"><@ofbizCurrency amount=preview.grossAmount isoCode="KRW"/></td>
    <td>${uiLabelMap.FrHrDeductionAmount}</td><td align="right"><@ofbizCurrency amount=preview.deductionAmount isoCode="KRW"/></td>
  </tr>
</table>
<h3>${uiLabelMap.FrHrNetAmount} : <@ofbizCurrency amount=preview.netAmount isoCode="KRW"/></h3>
<p>${uiLabelMap.FrHrPayrollPreviewNotice}</p>
</#if>
