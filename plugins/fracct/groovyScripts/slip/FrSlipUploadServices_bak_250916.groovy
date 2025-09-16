// 2025.09.16 한예린 백업
// 2019.10.24 윤서진 백업
// 2019.05.13 정해인 - 전표 엑셀(CSV) 일괄 업로드
// 업로드 전표는 결재선 무조건 대표이사(FRAPPR_CEO) - 재무팀 요청
//
// CSV 컬럼 (첫 줄 헤더, 엑셀에서 CSV UTF-8 로 저장)
//  0 전표키(같은 키 = 같은 전표) 1 전표일자(yyyy-MM-dd) 2 전표유형(RECEIPT/DISBURSE/TRANSFER) 3 부서ID 4 적요
//  5 계정과목 6 차대(D/C) 7 금액 8 거래처ID 9 메모

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.sql.Date

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil

def uploadFrSlipCsv() {
    println "### uploadFrSlipCsv 진입 : " + parameters
    String module = "FrSlipUploadServices.groovy"
    ByteBuffer buf = parameters.uploadedFile
    if (buf == null) {
        return error(UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctSlipUploadNoFile", locale))
    }
    byte[] bytes = new byte[buf.remaining()]
    buf.get(bytes)
    String text = new String(bytes, StandardCharsets.UTF_8)
    if (text.startsWith("﻿")) text = text.substring(1)

    // 전표키별로 묶기 (입력 순서 유지)
    Map<String, Map> slips = new LinkedHashMap<>()
    int lineNo = 0
    for (String line : text.split(/\r?\n/)) {
        lineNo++
        if (lineNo == 1 || !line.trim()) continue
        List<String> cols = line.split(",", -1).collect { it.trim().replaceAll(/^"|"$/, "") }
        if (cols.size() < 8) {
            return error("${lineNo}행 : 컬럼 수 부족 (${cols.size()})")
        }
        String key = cols[0]
        Map slip = slips.get(key)
        if (slip == null) {
            String slipType = cols[2].startsWith("FRSLIP_") ? cols[2] : "FRSLIP_" + cols[2]
            slip = [slipDate: Date.valueOf(cols[1]), slipTypeEnumId: slipType, deptPartyId: cols[3],
                    description: cols[4], items: [], lineNo: lineNo]
            slips.put(key, slip)
        }
        slip.items.add([glAccountId    : cols[5],
                        debitCreditFlag: cols[6].toUpperCase(),
                        amount         : new BigDecimal(cols[7].replace(",", "")),
                        partyId        : cols.size() > 8 && cols[8] ? cols[8] : null,
                        comments       : cols.size() > 9 && cols[9] ? cols[9] : null,
                        lineNo         : lineNo])
    }

    List slipIds = []
    for (Map.Entry<String, Map> entry : slips.entrySet()) {
        Map slip = entry.value
        Map res = dispatcher.runSync("createFrSlip", [slipTypeEnumId: slip.slipTypeEnumId, slipDate: slip.slipDate,
                deptPartyId: slip.deptPartyId, description: slip.description, userLogin: userLogin])
        if (ServiceUtil.isError(res)) {
            return error("전표키 ${entry.key} (${slip.lineNo}행) : " + ServiceUtil.getErrorMessage(res))
        }
        String slipId = res.slipId

        for (Map item : slip.items) {
            Map itemCtx = [slipId: slipId, glAccountId: item.glAccountId, debitCreditFlag: item.debitCreditFlag,
                           amount: item.amount, userLogin: userLogin]
            if (item.partyId) itemCtx.partyId = item.partyId
            if (item.comments) itemCtx.comments = item.comments
            Map itemRes = dispatcher.runSync("createFrSlipItem", itemCtx)
            if (ServiceUtil.isError(itemRes)) {
                return error("${item.lineNo}행 : " + ServiceUtil.getErrorMessage(itemRes))
            }
        }

        // 상신
        Map reqRes = dispatcher.runSync("requestFrSlip", [slipId: slipId, comments: "엑셀 업로드", userLogin: userLogin])
        if (ServiceUtil.isError(reqRes)) {
            return error("전표키 ${entry.key} (${slip.lineNo}행) : " + ServiceUtil.getErrorMessage(reqRes))
        }

        // 업로드 전표는 결재선 대표이사 고정 (2019.05 재무팀 요청)
        GenericValue frSlip = from("FrSlip").where(slipId: slipId).queryOne()
        frSlip.apprLevelEnumId = "FRAPPR_CEO"
        frSlip.store()

        slipIds.add(slipId)
    }

    Debug.logInfo("전표 업로드 ${parameters._uploadedFile_fileName} : ${slipIds.size()}건 ${slipIds}", module)
    Map result = success("전표 ${slipIds.size()}건 업로드 / 상신 완료 (결재선 대표이사)")
    result.slipIds = slipIds
    return result
}
