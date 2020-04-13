// 작성: 오픈브릿지시스템즈 이소라 / 2018.06.11
/*
 * 급여 명세서 화면 데이터 준비
 */

import org.apache.ofbiz.entity.GenericValue

println "########## PaySlip.groovy start"
println "########## PaySlip.groovy start"
payrollId = parameters.payrollId
if (payrollId) {
    GenericValue payroll = from("FrPayroll").where(payrollId: payrollId).queryOne()
    if (payroll) {
        context.payroll = payroll
        context.person = from("Person").where(partyId: payroll.partyId).queryOne()
        GenericValue emplInfo = from("FrEmplInfo").where(partyId: payroll.partyId).queryOne()
        context.emplInfo = emplInfo
        if (emplInfo?.departmentId) {
            context.department = from("PartyGroup").where(partyId: emplInfo.departmentId).cache().queryOne()
        }
        if (emplInfo?.positionGradeId) {
            context.positionGrade = from("Enumeration").where(enumId: emplInfo.positionGradeId).cache().queryOne()
        }
        context.statusItem = from("StatusItem").where(statusId: payroll.statusId).cache().queryOne()
        context.payYearMonthText = payroll.payYearMonth.substring(0, 4) + "." + payroll.payYearMonth.substring(4, 6)
    }
}
