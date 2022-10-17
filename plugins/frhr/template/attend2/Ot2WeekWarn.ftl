<#-- 2022.10.17 임재훈 연장근로 주 12시간 경고 (임시조치 - 경고만, 신청은 막지 않음) -->
<#if warnPartyId?has_content>
<div id="ot2WeekWarn" style="margin:5px 0;padding:6px;border:1px solid #ccc;">
  ${warnPartyId} / ${weekFrom} ~ ${weekTo} 연장근로 신청 합계(휴일 포함) : <b>${weekOtTotal}</b>시간
  (휴일 ${weekHoliTotal}시간)
  <#if weekOtOver == "Y">
    <span style="color:red;font-weight:bold;"> ※ 주 12시간 초과입니다. 인사팀 확인 바랍니다.</span>
  </#if>
</div>
</#if>
<script type="text/javascript">
// 신청 버튼 누를 때 이번 신청 시간 더해서 12시간 넘으면 경고 (확인 누르면 그대로 신청)
var ot2WeekTotal = ${weekOtTotal?c};
var ot2WeekParty = "${warnPartyId!}";
jQuery(document).ready(function() {
  var f = document.forms["Ot2RequestForm"];
  if (!f) return;
  jQuery(f).on("submit", function() {
    try {
      var s = new Date(f.startTime.value.replace(" ", "T"));
      var e = new Date(f.endTime.value.replace(" ", "T"));
      var hr = (e - s) / 3600000;
      if (isNaN(hr) || hr <= 0) return true;
      if (ot2WeekParty != "" && f.partyId.value != ot2WeekParty) return true;
      if (ot2WeekTotal + hr > 12) {
        return confirm("이번 주 연장근로 합계가 " + (ot2WeekTotal + hr) + "시간으로 12시간을 초과합니다.\n그래도 신청하시겠습니까?");
      }
    } catch (ex) {
      console.log(ex);
    }
    return true;
  });
});
</script>
