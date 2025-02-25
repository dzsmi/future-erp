/*
 * 퇴직금 정산 화면 데이터 (2025.02 조민석)
 */

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil

partyId = parameters.partyId
if (partyId) {
    java.sql.Date retireDate = null
    if (parameters.retireDate) {
        retireDate = java.sql.Date.valueOf(parameters.retireDate.toString().substring(0, 10))
    }
    Map severance = dispatcher.runSync("calcFrSeverancePay", [partyId: partyId, retireDate: retireDate, userLogin: userLogin])
    if (ServiceUtil.isError(severance)) {
        request.setAttribute("_ERROR_MESSAGE_", ServiceUtil.getErrorMessage(severance))
    } else {
        severance.partyId = partyId
        context.severance = severance
        context.payrollRows = severance.payrollRows
        context.person = from("Person").where(partyId: partyId).queryOne()
        GenericValue emplInfo = from("FrEmplInfo").where(partyId: partyId).queryOne()
        context.emplInfo = emplInfo
        if (emplInfo?.departmentId) {
            context.department = from("PartyGroup").where(partyId: emplInfo.departmentId).cache().queryOne()
        }
        if (emplInfo?.positionGradeId) {
            context.positionGrade = from("Enumeration").where(enumId: emplInfo.positionGradeId).cache().queryOne()
        }
        // 이미 퇴사 처리된 경우 정산 이력
        context.retireSettle = from("FrRetireSettle").where(partyId: partyId).orderBy("-settleDate").queryFirst()
    }
}
