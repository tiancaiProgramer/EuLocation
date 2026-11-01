package com.ux5.hyperos3.elemeisland;

import org.json.JSONObject;

public final class DidiPayloadTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        String title = "司机已到达\"上车点\"";
        String body = "车辆已到达，请准备上车\n下一行";
        String key = DidiPolicy.notificationKey(-123, "live");
        JSONObject root = new JSONObject(DidiPayload.build(title, body, key, "current-order", 100, true).toString());
        JSONObject v2 = root.getJSONObject("param_v2");
        check("taxi".equals(v2.getString("business")), "taxi business");
        check(v2.getInt("protocol") == 1, "OS2/3 wire protocol");
        check(v2.getString("orderId").equals("current-order"), "real order identity");
        check(v2.getString("notifyId").equals(key), "stable actual id/tag");
        check(v2.getLong("sequence") == 100, "sequence retained");
        check(v2.getJSONObject("baseInfo").getString("title").equals(title), "quoted title roundtrip");
        check(v2.getJSONObject("baseInfo").getString("content").equals(body), "newline content roundtrip");
        JSONObject island = v2.getJSONObject("param_island");
        check(island.getInt("islandTimeout") == 180, "bounded island expiry");
        check(v2.getInt("timeout") == 3, "focus timeout in minutes");
        check(island.getJSONObject("bigIslandArea").getJSONObject("imageTextInfoRight")
                .getJSONObject("textInfo").getString("title").equals(title), "big island title");
        check(island.getJSONObject("smallIslandArea").getJSONObject("picInfo").getString("pic")
                .equals(DidiPayload.PIC), "small island icon reference");
        check(!v2.optBoolean("cancel", false), "create does not cancel native notification");
        JSONObject noIcon = DidiPayload.build(title, body, key, key, 101, false).getJSONObject("param_v2");
        check(!noIcon.has("tickerPic"), "no dangling icon resource");
        check(!noIcon.getJSONObject("param_island").getJSONObject("smallIslandArea")
                .getJSONObject("picInfo").has("pic"), "no dangling small island icon");
        JSONObject notice = new JSONObject().put("title", "司机已接单").put("content", "正在接驾");
        JSONObject activity = new JSONObject().put("notificationData", notice.toString());
        JSONObject wrapper = new JSONObject().put("activityData", activity.toString());
        String[] parsed = DidiPayload.display(new JSONObject().put("activity_data", wrapper.toString()));
        check(parsed[0].equals("司机已接单") && parsed[1].equals("正在接驾"), "inspected serialized wrappers");
        parsed = DidiPayload.display(new JSONObject().put("data", notice));
        check(parsed[0].equals("司机已接单"), "object data wrapper");
        parsed = DidiPayload.display(new JSONObject().put("activity_data", "{broken"));
        check(parsed[0].isEmpty() && parsed[1].isEmpty(), "malformed data produces no invented status");
        parsed = DidiPayload.display(new JSONObject().put("unrelated", notice));
        check(parsed[0].isEmpty() && parsed[1].isEmpty(), "unrelated nested data is not searched");
        parsed = DidiPayload.display(new JSONObject().put("title", new JSONObject().put("title", "not text")));
        check(parsed[0].isEmpty(), "structured value is not a display string");
        System.out.println("PASS " + checks + " Didi payload/data cases");
    }
}
