<#-- 퇴직금 정산서 (2025.02 조민석) -->
<#if severance?has_content>
<div class="frhr-severance">
  <h2>${uiLabelMap.FrHrSeveranceStatement}</h2>
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
      <td class="label">${uiLabelMap.FrHrHireDate}</td><td>${(severance.hireDate?string("yyyy.MM.dd"))!}</td>
      <td class="label">${uiLabelMap.FrHrRetireDate}</td><td>${(severance.retireDate?string("yyyy.MM.dd"))!}</td>
    </tr>
    <tr>
      <td class="label">${uiLabelMap.FrHrServiceDays}</td><td>${severance.serviceDays!0}</td>
      <td class="label">${uiLabelMap.FrHrCalDays3Month}</td><td>${severance.calDays3Month!0}</td>
    </tr>
  </table>
  <br/>
  <table class="basic-table" cellspacing="0">
    <tr class="header-row">
      <td>${uiLabelMap.FrHrPayYearMonth}</td>
      <td>${uiLabelMap.FrHrBaseAmount}</td>
      <td>${uiLabelMap.FrHrOvertimeAmount}</td>
      <td>${uiLabelMap.FrHrSeveranceWageAmount}</td>
    </tr>
    <#list payrollRows! as row>
    <tr>
      <td>${row.payYearMonth}</td>
      <td align="right"><@ofbizCurrency amount=row.baseAmount!0 isoCode="KRW"/></td>
      <td align="right"><@ofbizCurrency amount=row.overtimeAmount!0 isoCode="KRW"/></td>
      <td align="right"><@ofbizCurrency amount=row.wageAmount!0 isoCode="KRW"/></td>
    </tr>
    </#list>
    <tr class="header-row">
      <td colspan="3">${uiLabelMap.FrHrWage3Month}</td>
      <td align="right"><@ofbizCurrency amount=severance.wage3MonthAmount!0 isoCode="KRW"/></td>
    </tr>
  </table>
  <br/>
  <table class="basic-table" cellspacing="0">
    <tr>
      <td class="label">${uiLabelMap.FrHrAvgDailyWage}</td>
      <td align="right">${severance.avgDailyWage!0} (${uiLabelMap.FrHrWage3Month} / ${severance.calDays3Month!0})</td>
    </tr>
    <tr>
      <td class="label">${uiLabelMap.FrHrRetirePay}</td>
      <td align="right"><@ofbizCurrency amount=severance.retirePayAmount!0 isoCode="KRW"/> (${uiLabelMap.FrHrAvgDailyWage} x 30 x ${severance.serviceDays!0} / 365)</td>
    </tr>
  </table>
  <p>${uiLabelMap.FrHrSeveranceStatementFooter}</p>
  <p>${nowTimestamp?string("yyyy.MM.dd")} (주)퓨처리테일 인사팀</p>
</div>
<#else>
  <h3>${uiLabelMap.FrHrErrorEmplInfoNotFound}</h3>
</#if>
