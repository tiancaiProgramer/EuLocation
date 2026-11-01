package com.ux5.hyperos3.elemeisland;

import org.json.JSONException;
import org.json.JSONObject;

/** Android-independent serialization and parsing, exercised by host tests. */
final class DidiPayload {
    static final String PIC = "miui.focus.pic_ux5_didi";

    static JSONObject build(String title, String body, String key, String order,
                            long sequence, boolean hasIcon) throws JSONException {
        JSONObject base = new JSONObject().put("title", title).put("content", body)
                .put("type", 2).put("colorTitle", "#FF7A00").put("colorTitleDark", "#FFA45B");
        JSONObject pic = new JSONObject().put("type", 1);
        if (hasIcon) pic.put("pic", PIC);
        JSONObject left = new JSONObject().put("type", 1).put("picInfo", pic);
        JSONObject right = new JSONObject().put("type", 2)
                .put("textInfo", new JSONObject().put("title", title));
        JSONObject island = new JSONObject().put("islandProperty", 1).put("islandOrder", false)
                .put("islandTimeout", 180)
                .put("bigIslandArea", new JSONObject().put("imageTextInfoLeft", left).put("imageTextInfoRight", right))
                .put("smallIslandArea", new JSONObject().put("picInfo", pic));
        JSONObject v2 = new JSONObject().put("protocol", 1).put("business", "taxi")
                .put("enableFloat", true).put("updatable", true).put("islandFirstFloat", true)
                .put("reopen", "reopen").put("timeout", 3).put("filterWhenNoPermission", false)
                .put("showSmallIcon", true).put("isShowNotification", true)
                .put("notifyId", key).put("orderId", order).put("sequence", sequence)
                .put("ticker", title).put("aodTitle", title)
                .put("baseInfo", base).put("param_island", island);
        if (hasIcon) v2.put("tickerPic", PIC).put("tickerPicDark", PIC).put("aodPic", PIC);
        return new JSONObject().put("param_v2", v2);
    }

    static String[] display(JSONObject json) {
        String title = scalar(json, "title");
        String content = scalar(json, "content");
        JSONObject activity = child(json, "activity_data");
        if (activity == null) activity = child(json, "data");
        if (activity == null) activity = json;
        JSONObject inner = child(activity, "activityData");
        if (inner != null) activity = inner;
        if (title.isEmpty()) title = scalar(activity, "title");
        if (content.isEmpty()) content = scalar(activity, "content");
        JSONObject notice = child(activity, "notificationData");
        if (notice != null) {
            if (title.isEmpty()) title = scalar(notice, "title");
            if (content.isEmpty()) content = scalar(notice, "content");
        }
        return new String[]{title, content};
    }

    private static String scalar(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static JSONObject child(JSONObject parent, String key) {
        Object value = parent.opt(key);
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) {
            try { return new JSONObject((String) value); } catch (Exception ignored) { }
        }
        return null;
    }
}
