<#-- 작성: 오픈브릿지시스템즈 이소라 / 2018.06.11 -->
<#-- 급여 명세서 -->
<#if payroll?has_content>
<div class="frhr-payslip">
  <h2>${payYearMonthText!} ${uiLabelMap.FrHrPaySlip}</h2>
  <table class="basic-table" cellspacing="0">
    <tr>
      <td class="label">${uiLabelMap.FrHrEmplNo}</td><td>${(emplInfo.emplNo)!}</td>
      <td class="label">${uiLabelMap.FrHrEmployee}</td><td>${(person.lastName)!}${(person.firstName)!}</td>
    </tr>
    <tr>
      <td class="label">${uiLabelMap.FrHrDepartment}</td><td>${(department.groupName)!}</td>
      <td class="label">${uiLabelMap.FrHrPositionGrade}</td><td>${(positionGrade.description)!}</td>
    </tr>
    <tr>
      <td class="label">${uiLabelMap.CommonStatus}</td><td>${(statusItem.description)!}</td>
      <td class="label">${uiLabelMap.FrHrWorkDays}</td><td><#if payroll.workDays?has_content>${payroll.workDays} (${uiLabelMap.FrHrProrated})<#else>-</#if></td>
    </tr>
  </table>
  <br/>
  <table class="basic-table" cellspacing="0">
    <tr class="header-row">
      <td colspan="2">${uiLabelMap.FrHrPayItems}</td>
      <td colspan="2">${uiLabelMap.FrHrDeductionItems}</td>
    </tr>
    <tr>
      <td>${uiLabelMap.FrHrBaseAmount}<#if "Y" == (payroll.probationYn)!""> (${uiLabelMap.FrHrProbationApplied})</#if></td><td align="right"><@ofbizCurrency amount=payroll.baseAmount!0 isoCode="KRW"/></td>
      <td>${uiLabelMap.FrHrPensionAmount}</td><td align="right"><@ofbizCurrency amount=payroll.pensionAmount!0 isoCode="KRW"/></td>
    </tr>
    <tr>
      <td>${uiLabelMap.FrHrOvertimeAmount} (${payroll.overtimeHours!0}h)</td><td align="right"><@ofbizCurrency amount=payroll.overtimeAmount!0 isoCode="KRW"/></td>
      <td>${uiLabelMap.FrHrHealthAmount}</td><td align="right"><@ofbizCurrency amount=payroll.healthAmount!0 isoCode="KRW"/></td>
    </tr>
    <tr>
      <td>${uiLabelMap.FrHrBonusAmount}</td><td align="right"><@ofbizCurrency amount=payroll.bonusAmount!0 isoCode="KRW"/></td>
      <td>${uiLabelMap.FrHrLongtermAmount}</td><td align="right"><@ofbizCurrency amount=payroll.longtermAmount!0 isoCode="KRW"/></td>
    </tr>
    <tr>
      <td></td><td></td>
      <td>${uiLabelMap.FrHrEmployAmount}</td><td align="right"><@ofbizCurrency amount=payroll.employAmount!0 isoCode="KRW"/></td>
    </tr>
    <tr>
      <td></td><td></td>
      <td>${uiLabelMap.FrHrIncomeTaxAmount}</td><td align="right"><@ofbizCurrency amount=payroll.incomeTaxAmount!0 isoCode="KRW"/></td>
    </tr>
    <tr class="header-row">
      <td>${uiLabelMap.FrHrGrossAmount}</td><td align="right"><@ofbizCurrency amount=payroll.grossAmount!0 isoCode="KRW"/></td>
      <td>${uiLabelMap.FrHrDeductionAmount}</td><td align="right"><@ofbizCurrency amount=payroll.deductionAmount!0 isoCode="KRW"/></td>
    </tr>
  </table>
  <h3>${uiLabelMap.FrHrNetAmount} : <@ofbizCurrency amount=payroll.netAmount!0 isoCode="KRW"/></h3>
  <p>${uiLabelMap.FrHrPaySlipFooter}</p>
</div>
<#else>
  <h3>${uiLabelMap.FrHrPayrollNotFound}</h3>
</#if>
