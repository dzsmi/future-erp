// 작성: (주)핀링크 송태호 / 2021.02.08
/*
 * 법인카드 승인내역 수신
 *  - receiveFrCardTxFile : 수신 디렉토리(fracct.card.recvDir)의 CARD_yyyyMMdd*.csv 일괄 적재 (스케줄 FR_CARD_RECV, 매일 07:00)
 *  - uploadFrCardTxFile  : 화면에서 파일 직접 업로드 (재수신, 누락분 보정용)
 *  - 적재 후 사용 제한 검사(checkFrCardTxRestrict) 호출 (2021.02.12)
 *
 * 카드사 파일 형식 (샘플 : data/card/FrCardTxSample.csv, 샘플은 UTF-8 / 실제 파일은 MS949)
 *  첫 줄 헤더, 콤마 구분, 금액 콤마 없음
 *   0 카드번호(마스킹)  1 승인번호  2 승인일자(yyyyMMdd)  3 승인시각(HHmmss)
 *   4 가맹점명  5 가맹점사업자번호  6 업종명  7 업종코드
 *   8 공급가액  9 부가세  10 승인금액  11 취소구분(Y: 승인취소)
 *
 * 적재 규칙
 *  - 카드번호 + 승인번호 기준 1건만 적재 (재수신 시 중복 건너뜀)
 *  - 취소구분 Y 는 원승인 건을 찾아 승인취소(FRCT_CANCELLED) 처리. 원승인 건이 없으면 건너뜀
 *  - 이미 회계 거래가 생성된 건이 취소되면 상태는 그대로 두고 cancelYn=Y 만 표시 (재경팀 역분개)
 */

import java.nio.ByteBuffer
import java.sql.Timestamp
import java.text.SimpleDateFormat

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtilProperties

def receiveFrCardTxFile() {
    String module = "FrCardRecvServices.groovy"
    String recvDir = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.recvDir", delegator)
    String doneDir = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.doneDir", delegator)
    String charset = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.fileCharset", "MS949", delegator)

    File dir = recvDir ? new File(recvDir) : null
    if (dir == null || !dir.isDirectory()) {
        return error(UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctCardRecvDirNotFound", [recvDir: recvDir], locale))
    }
    List<File> files = dir.listFiles().findAll { File f -> f.isFile() && f.name ==~ /CARD_\d{8}.*\.csv/ }.sort { it.name }
    if (!files) {
        Debug.logInfo("법인카드 수신 파일 없음 : " + recvDir, module)
        return success()
    }

    List recvFileIds = []
    for (File f : files) {
        Map recv = storeFrCardTxText(f.getText(charset), f.name)
        run service: "checkFrCardTxRestrict", with: [recvFileId: recv.recvFileId]
        recvFileIds.add(recv.recvFileId)
        Debug.logInfo("법인카드 수신 ${f.name} : ${recv}", module)
        if (doneDir) {
            File done = new File(doneDir)
            done.mkdirs()
            f.renameTo(new File(done, f.name))
        }
    }

    Map result = success()
    result.recvFileIds = recvFileIds
    return result
}

// 2021.12.13 기존 메소드 백업
def receiveFrCardTxFileOld() {
    String module = "FrCardRecvServices.groovy"
    String recvDir = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.recvDir", delegator)
    String doneDir = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.doneDir", delegator)
    String charset = EntityUtilProperties.getPropertyValue("fracct", "fracct.card.fileCharset", "MS949", delegator)

    File dir = recvDir ? new File(recvDir) : null
    if (dir == null || !dir.isDirectory()) {
        return error(UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctCardRecvDirNotFound", [recvDir: recvDir], locale))
    }
    List<File> files = dir.listFiles().findAll { File f -> f.isFile() && f.name ==~ /CARD_\d{8}.*\.csv/ }.sort { it.name }
    if (!files) {
        Debug.logInfo("법인카드 수신 파일 없음 : " + recvDir, module)
        return success()
    }

    List recvFileIds = []
    for (File f : files) {
        Map recv = storeFrCardTxText(f.getText(charset), f.name)
        run service: "checkFrCardTxRestrict", with: [recvFileId: recv.recvFileId]
        recvFileIds.add(recv.recvFileId)
        Debug.logInfo("법인카드 수신 ${f.name} : ${recv}", module)
        if (doneDir) {
            File done = new File(doneDir)
            done.mkdirs()
            f.renameTo(new File(done, f.name))
        }
    }

    Map result = success()
    result.recvFileIds = recvFileIds
    return result
}

def uploadFrCardTxFile() {
    String charset = parameters.fileCharset ?: EntityUtilProperties.getPropertyValue("fracct", "fracct.card.fileCharset", "MS949", delegator)
    ByteBuffer buf = parameters.uploadedFile
    if (buf == null) {
        return error(UtilProperties.getMessage("FrAcctErrorUiLabels", "FrAcctCardUploadNoFile", locale))
    }
    byte[] bytes = new byte[buf.remaining()]
    buf.get(bytes)
    String text = new String(bytes, charset)
    if (text.startsWith("﻿")) text = text.substring(1)

    Map recv = storeFrCardTxText(text, parameters._uploadedFile_fileName ?: "upload.csv")
    run service: "checkFrCardTxRestrict", with: [recvFileId: recv.recvFileId]
    Map result = success(UtilProperties.getMessage("FrAcctUiLabels", "FrAcctCardRecvResult", recv, locale))
    result.recvFileIds = [recv.recvFileId]
    return result
}

/*
 * 파일 내용 → FrCardTx 적재 + FrCardRecvFile 이력
 */
Map storeFrCardTxText(String text, String fileName) {
    Timestamp now = UtilDateTime.nowTimestamp()
    String recvFileId = delegator.getNextSeqId("FrCardRecvFile")
    SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss")
    fmt.setLenient(false)

    int totalCnt = 0, insertCnt = 0, cancelCnt = 0, skipCnt = 0
    List<String> errors = []
    int lineNo = 0
    for (String line : text.split(/\r?\n/)) {
        lineNo++
        if (lineNo == 1 || !line.trim()) continue
        totalCnt++
        List<String> cols = line.split(",", -1).collect { it.trim() }
        if (cols.size() < 12) {
            errors.add("${lineNo}행 컬럼 수 ${cols.size()}")
            continue
        }
        String cardNo = cols[0]
        String approvalNo = cols[1]
        Timestamp txDate
        BigDecimal supplyAmount, vatAmount, amount
        try {
            txDate = new Timestamp(fmt.parse(cols[2] + cols[3].padLeft(6, "0")).time)
            supplyAmount = new BigDecimal(cols[8] ?: "0")
            vatAmount = new BigDecimal(cols[9] ?: "0")
            amount = new BigDecimal(cols[10])
        } catch (Exception e) {
            errors.add("${lineNo}행 형식 오류 (${e.message})")
            continue
        }

        GenericValue exist = from("FrCardTx").where(cardNo: cardNo, approvalNo: approvalNo).queryFirst()

        // 승인취소
        if ("Y".equalsIgnoreCase(cols[11])) {
            if (exist == null || "Y" == exist.cancelYn) {
                skipCnt++
                continue
            }
            exist.cancelYn = "Y"
            if (!exist.acctgTransId) {
                exist.statusId = "FRCT_CANCELLED"
            }
            exist.lastUpdatedByUserLogin = userLogin?.userLoginId
            exist.store()
            cancelCnt++
            continue
        }

        if (exist != null) {
            skipCnt++
            continue
        }
        GenericValue cardTx = delegator.makeValue("FrCardTx", [
                cardTxId      : delegator.getNextSeqId("FrCardTx"),
                cardNo        : cardNo,
                approvalNo    : approvalNo,
                txDate        : txDate,
                merchantName  : cols[4],
                merchantBizNo : cols[5],
                mccName       : cols[6],
                mccCode       : cols[7],
                supplyAmount  : supplyAmount,
                vatAmount     : vatAmount,
                amount        : amount,
                cancelYn      : "N",
                statusId      : "FRCT_RECEIVED",
                recvFileId    : recvFileId,
                receivedDate  : now,
                lastUpdatedByUserLogin: userLogin?.userLoginId])
        cardTx.create()
        insertCnt++
    }

    GenericValue recvFile = delegator.makeValue("FrCardRecvFile", [
            recvFileId     : recvFileId,
            fileName       : fileName,
            recvDate       : now,
            totalCnt       : totalCnt,
            insertCnt      : insertCnt,
            cancelCnt      : cancelCnt,
            skipCnt        : skipCnt,
            errorCnt       : errors.size(),
            resultMsg      : errors ? errors.join("\n") : null,
            recvByUserLogin: userLogin?.userLoginId])
    recvFile.create()

    return [recvFileId: recvFileId, fileName: fileName, totalCnt: totalCnt, insertCnt: insertCnt,
            cancelCnt: cancelCnt, skipCnt: skipCnt, errorCnt: errors.size()]
}
