// 작성: 정해인 / 2021.04.12
/*
 * 법인카드 소명 화면 (FrCardExplain)
 *  - 기본 : 소명필요(FRCT_EXPLAIN_REQ) 건만, showAll=Y 이면 전체
 *  - 재경팀(FRACCT_ADMIN) 이 아니면 본인 카드(FrCardMaster.holderPartyId = 로그인 사용자) 만
 *  - 위반 사유 : 주말 / 심야(23~06시) / 제한업종(fracct.properties cardRestrictMcc)
 */

import java.sql.Timestamp

import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtilProperties

String mccStr = EntityUtilProperties.getPropertyValue("fracct", "cardRestrictMcc", "", delegator)
List restrictMcc = mccStr ? mccStr.split(",").collect { it.trim() } : []

List conds = []
if (parameters.showAll != "Y") {
    conds.add(EntityCondition.makeCondition("statusId", "FRCT_EXPLAIN_REQ"))
}
if (parameters.cardNo) {
    conds.add(EntityCondition.makeCondition("cardNo", parameters.cardNo))
}
boolean isAdmin = security.hasPermission("FRACCT_ADMIN", userLogin)
if (!isAdmin) {
    List myCards = from("FrCardMaster").where("holderPartyId", userLogin.partyId).queryList()
    conds.add(EntityCondition.makeCondition("cardNo", EntityOperator.IN, myCards ? myCards*.cardNo : ["_NA_"]))
}

List cardTxList = from("FrCardTx").where(conds ? EntityCondition.makeCondition(conds, EntityOperator.AND) : null)
        .orderBy("-txDate").queryList()

List explainList = []
for (cardTx in cardTxList) {
    Map row = [:]
    row.putAll(cardTx)

    List reasons = []
    Calendar cal = Calendar.getInstance()
    cal.setTime(cardTx.txDate)
    int dow = cal.get(Calendar.DAY_OF_WEEK)
    int hour = cal.get(Calendar.HOUR_OF_DAY)
    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) reasons.add("주말")
    if (hour >= 23 || hour < 6) reasons.add("심야")
    if (cardTx.mccName && restrictMcc.contains(cardTx.mccName.trim())) reasons.add("제한업종(" + cardTx.mccName.trim() + ")")
    row.dispReason = reasons.join(", ")

    explainList.add(row)
}
context.explainList = explainList
context.explainCount = explainList.size()
context.isCardAdmin = isAdmin
