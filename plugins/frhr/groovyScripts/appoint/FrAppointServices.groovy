// 작성: 한예린 / 2025.06.09
/*
 * [SR-2025-1107] 인사발령 (승진/전보) 신청·승인 (인사팀 요청)
 *  - 신청 : 발령유형(승진/전보), 발령일, 변경 후 직급/부서/기본급
 *           신청 시점의 직급/부서/기본급을 from 값으로 저장
 *  - 같은 직원의 신청중(FRAPT_REQUESTED) 발령이 있으면 신청 불가
 */

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue

def requestFrAppoint() {
    GenericValue emplInfo = from("FrEmplInfo").where(partyId: parameters.partyId).queryOne()
    if (!emplInfo) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorEmplInfoNotFound", locale))
    }
    if (emplInfo.retireDate) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorAlreadyRetired", locale))
    }
    GenericValue pending = from("FrAppoint").where(partyId: parameters.partyId, statusId: "FRAPT_REQUESTED").queryFirst()
    if (pending) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorAppointPending", [pending.appointId] as Object[], locale))
    }

    String appointTypeEnumId = parameters.appointTypeEnumId
    // 승진은 직급 필수, 전보는 부서 필수
    if ("FRAPT_PROMOTION" == appointTypeEnumId && !parameters.toPositionGradeId) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorAppointGradeRequired", locale))
    }
    if ("FRAPT_TRANSFER" == appointTypeEnumId && !parameters.toDepartmentId) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorAppointDeptRequired", locale))
    }

    GenericValue appoint = delegator.makeValue("FrAppoint")
    appoint.appointId = delegator.getNextSeqId("FrAppoint")
    appoint.partyId = parameters.partyId
    appoint.appointTypeEnumId = appointTypeEnumId
    appoint.effectiveDate = parameters.effectiveDate
    appoint.fromPositionGradeId = emplInfo.positionGradeId
    appoint.toPositionGradeId = parameters.toPositionGradeId ?: emplInfo.positionGradeId
    appoint.fromDepartmentId = emplInfo.departmentId
    appoint.toDepartmentId = parameters.toDepartmentId ?: emplInfo.departmentId
    appoint.fromBaseSalary = emplInfo.baseSalary
    appoint.toBaseSalary = parameters.toBaseSalary ?: emplInfo.baseSalary
    appoint.reason = parameters.reason
    appoint.statusId = "FRAPT_REQUESTED"
    appoint.requestedByUserLogin = userLogin?.userLoginId
    appoint.requestDate = UtilDateTime.nowTimestamp()
    appoint.create()

    Map result = success(UtilProperties.getMessage("FrHrUiLabels", "FrHrAppointRequestSuccess", locale))
    result.appointId = appoint.appointId
    return result
}

def cancelFrAppoint() {
    GenericValue appoint = from("FrAppoint").where(appointId: parameters.appointId).queryOne()
    if (!appoint || appoint.statusId != "FRAPT_REQUESTED") {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorAppointNotRequested", locale))
    }
    appoint.statusId = "FRAPT_CANCELLED"
    appoint.store()
    return success()
}
