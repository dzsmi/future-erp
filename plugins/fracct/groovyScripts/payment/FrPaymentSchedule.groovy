// 2019.01.21 정해인 - 지급예정표 화면

import java.sql.Date

import org.apache.ofbiz.base.util.UtilValidate

Date fromDueDate = null
Date thruDueDate = null
try {
    if (UtilValidate.isNotEmpty(parameters.fromDueDate)) fromDueDate = Date.valueOf(parameters.fromDueDate.toString().substring(0, 10))
    if (UtilValidate.isNotEmpty(parameters.thruDueDate)) thruDueDate = Date.valueOf(parameters.thruDueDate.toString().substring(0, 10))
} catch (Exception e) {
    fromDueDate = null
    thruDueDate = null
}

Map ctx = [userLogin: userLogin, includePaid: parameters.includePaid ?: "N"]
if (fromDueDate) ctx.fromDueDate = fromDueDate
if (thruDueDate) ctx.thruDueDate = thruDueDate
if (parameters.partyId) ctx.partyId = parameters.partyId

Map result = dispatcher.runSync("getFrPaymentSchedule", ctx)
context.scheduleList = result.scheduleList
context.totalAmount = result.totalAmount
context.scheduleCount = result.scheduleList ? result.scheduleList.size() : 0
