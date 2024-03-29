// 2024.03.29 한예린 백업
// 작성: (주)핀링크 송태호 / 2021.02.12
/*
 * 법인카드 사용 제한 검사
 *  - 수신(FRCT_RECEIVED) 건 중 아래에 해당하면 소명필요(FRCT_EXPLAIN_REQ) 로 변경, 위반 사유 기록
 *      1) 주말 사용 (토/일)
 *      2) 심야 사용 (23:00 ~ 06:00)
 *      3) 사용 제한 업종 (카드사 업종명 기준)
 *  - 소명필요 건은 소명 완료(FRCT_EXPLAINED) 전까지 자동전표 대상에서 제외
 *  - 수신 서비스(receiveFrCardTxFile / uploadFrCardTxFile) 적재 후 호출, 화면에서 재검사 가능
 *
 *  ※ 제한 업종은 재경팀 법인카드 사용지침(2021.01) 기준
 */

import java.sql.Timestamp

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

// 사용 제한 업종 (카드사 업종명)
RESTRICT_MCC_LIST = ['유흥주점', '단란주점', '골프장', '노래방']
NIGHT_FROM_HOUR = 23
NIGHT_TO_HOUR = 6

def checkFrCardTxRestrict() {
    String module = "FrCardRestrictServices.groovy"

    List conds = [EntityCondition.makeCondition("statusId", "FRCT_RECEIVED")]
    if (parameters.cardTxId) conds.add(EntityCondition.makeCondition("cardTxId", parameters.cardTxId))
    if (parameters.recvFileId) conds.add(EntityCondition.makeCondition("recvFileId", parameters.recvFileId))
    List<GenericValue> cardTxList = from("FrCardTx").where(EntityCondition.makeCondition(conds, EntityOperator.AND)).queryList()

    int checkCnt = 0, violateCnt = 0
    for (GenericValue cardTx : cardTxList) {
        checkCnt++
        List<String> reasons = getRestrictReasons(cardTx.txDate, cardTx.mccName)
        if (!reasons) continue

        cardTx.statusId = "FRCT_EXPLAIN_REQ"
        cardTx.violationReason = reasons.join(",")
        cardTx.lastUpdatedByUserLogin = userLogin?.userLoginId
        cardTx.store()
        violateCnt++
    }
    Debug.logInfo("법인카드 사용 제한 검사 : 대상 ${checkCnt}건 / 소명필요 ${violateCnt}건", module)

    Map result = success()
    result.checkCnt = checkCnt
    result.violateCnt = violateCnt
    return result
}

List<String> getRestrictReasons(Timestamp txDate, String mccName) {
    List<String> reasons = []
    if (txDate != null) {
        Calendar cal = Calendar.getInstance()
        cal.setTime(txDate)
        int dow = cal.get(Calendar.DAY_OF_WEEK)
        int hour = cal.get(Calendar.HOUR_OF_DAY)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
            reasons.add("주말")
        }
        if (hour >= NIGHT_FROM_HOUR || hour < NIGHT_TO_HOUR) {
            reasons.add("심야")
        }
    }
    if (mccName && RESTRICT_MCC_LIST.contains(mccName.trim())) {
        reasons.add("제한업종(" + mccName.trim() + ")")
    }
    return reasons
}
