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
import org.apache.ofbiz.service.ServiceUtil

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

/*
 * 2025.06.13 한예린 [SR-2025-1107] 인사발령 승인
 *  - 승인은 인사팀(FRHR_ADMIN)만
 *  - OFBiz 코어 반영 (humanres 화면과 맞추기 위해)
 *     . 직급/기본급 변경 : 진행중 Employment 종료(updateEmployment, thruDate=발령일)
 *                         -> 발령일자로 Employment 재생성(createEmployment, PayHistory 같이 생성)
 *     . 부서 변경 : 기존 부서 PartyRelationship 종료(updatePartyRelationship)
 *                  -> 새 부서 PartyRelationship 생성(createPartyRelationship, EMPLOYMENT)
 *  - FrEmplInfo 직급/부서/기본급은 승인 즉시 변경
 */
def approveFrAppoint() {
    if (!security.hasPermission("FRHR_ADMIN", userLogin)) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrAdminPermissionError", locale))
    }
    GenericValue appoint = from("FrAppoint").where(appointId: parameters.appointId).queryOne()
    if (!appoint || appoint.statusId != "FRAPT_REQUESTED") {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorAppointNotRequested", locale))
    }
    GenericValue emplInfo = from("FrEmplInfo").where(partyId: appoint.partyId).queryOne()
    if (!emplInfo) {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorEmplInfoNotFound", locale))
    }

    java.sql.Timestamp effectiveTs = new java.sql.Timestamp(appoint.effectiveDate.getTime())
    String companyPartyId = UtilProperties.getPropertyValue("frhr", "company.partyId", "Company")
    // 코어 서비스 권한(humanResManagerPermission, partyRelationshipPermissionCheck) 때문에 system 으로 호출
    GenericValue sysLogin = from("UserLogin").where(userLoginId: "system").cache().queryOne()

    boolean gradeChanged = appoint.toPositionGradeId && appoint.toPositionGradeId != appoint.fromPositionGradeId
    boolean salaryChanged = appoint.toBaseSalary != null && appoint.fromBaseSalary != null && appoint.toBaseSalary.compareTo(appoint.fromBaseSalary) != 0
    boolean deptChanged = appoint.toDepartmentId && appoint.toDepartmentId != appoint.fromDepartmentId

    // 1) Employment
    if (gradeChanged || salaryChanged) {
        List<GenericValue> employments = from("Employment").where(partyIdTo: appoint.partyId, roleTypeIdTo: "EMPLOYEE").filterByDate().queryList()
        for (GenericValue employment : employments) {
            Map updResult = dispatcher.runSync("updateEmployment", [roleTypeIdFrom: employment.roleTypeIdFrom, roleTypeIdTo: employment.roleTypeIdTo,
                    partyIdFrom: employment.partyIdFrom, partyIdTo: employment.partyIdTo, fromDate: employment.fromDate,
                    thruDate: effectiveTs, userLogin: sysLogin])
            if (ServiceUtil.isError(updResult)) {
                return error(ServiceUtil.getErrorMessage(updResult))
            }
        }
        Map empResult = dispatcher.runSync("createEmployment", [roleTypeIdFrom: "INTERNAL_ORGANIZATIO", partyIdFrom: companyPartyId,
                roleTypeIdTo: "EMPLOYEE", partyIdTo: appoint.partyId, fromDate: effectiveTs,
                amount: appoint.toBaseSalary, periodTypeId: "RATE_MONTH", comments: "인사발령 " + appoint.appointId,
                userLogin: sysLogin])
        if (ServiceUtil.isError(empResult)) {
            return error(ServiceUtil.getErrorMessage(empResult))
        }
    }

    // 2) 부서 PartyRelationship
    if (deptChanged) {
        if (appoint.fromDepartmentId) {
            List<GenericValue> rels = from("PartyRelationship").where(partyIdFrom: appoint.fromDepartmentId, partyIdTo: appoint.partyId,
                    roleTypeIdTo: "EMPLOYEE", partyRelationshipTypeId: "EMPLOYMENT").filterByDate().queryList()
            for (GenericValue rel : rels) {
                Map relResult = dispatcher.runSync("updatePartyRelationship", [partyIdFrom: rel.partyIdFrom, partyIdTo: rel.partyIdTo,
                        roleTypeIdFrom: rel.roleTypeIdFrom, roleTypeIdTo: rel.roleTypeIdTo, fromDate: rel.fromDate,
                        thruDate: effectiveTs, userLogin: sysLogin])
                if (ServiceUtil.isError(relResult)) {
                    return error(ServiceUtil.getErrorMessage(relResult))
                }
            }
        }
        dispatcher.runSync("ensurePartyRole", [partyId: appoint.toDepartmentId, roleTypeId: "INTERNAL_ORGANIZATIO", userLogin: sysLogin])
        dispatcher.runSync("ensurePartyRole", [partyId: appoint.partyId, roleTypeId: "EMPLOYEE", userLogin: sysLogin])
        Map newRelResult = dispatcher.runSync("createPartyRelationship", [partyIdFrom: appoint.toDepartmentId, roleTypeIdFrom: "INTERNAL_ORGANIZATIO",
                partyIdTo: appoint.partyId, roleTypeIdTo: "EMPLOYEE", partyRelationshipTypeId: "EMPLOYMENT",
                fromDate: effectiveTs, comments: "인사발령 " + appoint.appointId, userLogin: sysLogin])
        if (ServiceUtil.isError(newRelResult)) {
            return error(ServiceUtil.getErrorMessage(newRelResult))
        }
    }

    // 3) 인사정보
    emplInfo.positionGradeId = appoint.toPositionGradeId ?: emplInfo.positionGradeId
    emplInfo.departmentId = appoint.toDepartmentId ?: emplInfo.departmentId
    if (appoint.toBaseSalary != null) {
        emplInfo.baseSalary = appoint.toBaseSalary
    }
    emplInfo.store()

    appoint.statusId = "FRAPT_APPROVED"
    appoint.approvedByUserLogin = userLogin?.userLoginId
    appoint.approvedDate = UtilDateTime.nowTimestamp()
    appoint.comments = parameters.comments
    appoint.store()

    println "appoint approve >>> " + appoint.appointId + " grade=" + gradeChanged + " salary=" + salaryChanged + " dept=" + deptChanged
    return success(UtilProperties.getMessage("FrHrUiLabels", "FrHrAppointApproveSuccess", locale))
}

def rejectFrAppoint() {
    GenericValue appoint = from("FrAppoint").where(appointId: parameters.appointId).queryOne()
    if (!appoint || appoint.statusId != "FRAPT_REQUESTED") {
        return error(UtilProperties.getMessage("FrHrUiLabels", "FrHrErrorAppointNotRequested", locale))
    }
    appoint.statusId = "FRAPT_REJECTED"
    appoint.approvedByUserLogin = userLogin?.userLoginId
    appoint.approvedDate = UtilDateTime.nowTimestamp()
    appoint.comments = parameters.comments
    appoint.store()
    return success()
}
