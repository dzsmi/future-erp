// 2022.07.11 임재훈
// 결재 알림 메일 - VOC "전표 상신해도 결재자한테 메일이 안 감" (재무팀)
// 기존 결재 알림 서비스(sendApprNotiMail) 동작 안 하는 것 같아서 새로 만듦 -> sendApprNotiMail2
// secas.xml 에서 requestFrSlip / uploadFrSlipCsv 끝나면(commit) 호출
// TODO 결재자 개인 메일 매핑 필요 (지금은 결재레벨별 공용 메일로 보냄)

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtilProperties
import org.apache.ofbiz.service.ServiceUtil

def sendApprNotiMail2() {
    // 왜 이렇게 되어있는지 모르겠음
    String module = "FrApprNotiMail2.groovy"
    println "### sendApprNotiMail2 : " + parameters.slipId + " / " + parameters.slipIds

    if ("Y" != EntityUtilProperties.getPropertyValue("fracct", "appr.noti.mail.useYn", "Y", delegator)) {
        return success()
    }

    List slipIds = []
    if (parameters.slipId) slipIds.add(parameters.slipId)
    if (parameters.slipIds) slipIds.addAll(parameters.slipIds)   // 엑셀 업로드

    String mailFrom = EntityUtilProperties.getPropertyValue("fracct", "appr.noti.mail.from", "erp-noreply@futureretail.example.com", delegator)
    for (String slipId : slipIds) {
        GenericValue slip = from("FrSlip").where(slipId: slipId).queryOne()
        if (slip == null || slip.statusId != "FRSLIP_REQUESTED") continue

        // 결재레벨별 공용 메일 (appr.noti.mail.to.FRAPPR_TEAM ...)
        String mailTo = EntityUtilProperties.getPropertyValue("fracct", "appr.noti.mail.to." + slip.apprLevelEnumId, delegator)
        if (!mailTo) mailTo = EntityUtilProperties.getPropertyValue("fracct", "appr.noti.mail.to", delegator)
        if (!mailTo) {
            Debug.logWarning("결재 알림 메일 수신자 없음 slipId=" + slipId, module)
            continue
        }

        String subject = "[ERP 결재요청] 전표 " + slip.slipId + " (" + (slip.description ?: "") + ")"
        String body = "전표번호 : " + slip.slipId + "\n" +
                "전표일자 : " + slip.slipDate + "\n" +
                "금액 : " + slip.amount + "\n" +
                "결재레벨 : " + slip.apprLevelEnumId + "\n" +
                "작성자 : " + slip.createdByUserLogin + "\n\n" +
                "ERP 재경관리 > 결재 메뉴에서 확인하세요."
        try {
            Map res = dispatcher.runSync("sendMail", [sendTo: mailTo, sendFrom: mailFrom, subject: subject, body: body,
                                                      contentType: "text/plain", userLogin: userLogin])
            if (ServiceUtil.isError(res)) {
                Debug.logWarning("결재 알림 메일 실패 " + slipId + " : " + ServiceUtil.getErrorMessage(res), module)
            }
        } catch (Exception e) {
            // 임시조치 - 메일 실패해도 상신은 되게
            Debug.logError(e, "결재 알림 메일 오류 " + slipId, module)
        }
    }
    return success()
}
