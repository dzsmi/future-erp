// 작성: 오픈브릿지시스템즈 이소라 / 2018.06.11
/*
 * 결산 마감 조회 화면 데이터 준비
 * 조회년도(closeYear)의 1~12월을 모두 보여주고, FrClosePeriod 가 없는 달은 미마감(N)으로 표시
 */

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue

String closeYear = parameters.closeYear ?: UtilDateTime.nowDateString('yyyy')

List closePeriodList = []
for (int month = 1; month <= 12; month++) {
    String closeYearMonth = closeYear + String.format('%02d', month)
    GenericValue closePeriod = from('FrClosePeriod').where('closeYearMonth', closeYearMonth).queryOne()
    closePeriodList.add([closeYearMonth: closeYearMonth,
                         isClosed: closePeriod?.isClosed ?: 'N',
                         closedByUserLogin: closePeriod?.closedByUserLogin,
                         closedDate: closePeriod?.closedDate,
                         comments: closePeriod?.comments])
}

context.closeYear = closeYear
context.closePeriodList = closePeriodList
