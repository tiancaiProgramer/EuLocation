package com.ux5.hyperos3.perappcn;

import org.json.JSONArray;
import org.json.JSONObject;

/** Native configuration extension shared by both original Search variants. */
final class SearchEngineConfig {
    static String extend(String text, boolean phone) throws Exception {
        JSONObject root = new JSONObject(text);
        JSONObject data = root.getJSONObject("data");
        if (phone) {
            JSONObject scene = data.getJSONObject("searchEngineSceneMap").getJSONObject("globalSearchSearchBox");
            JSONArray list = scene.getJSONArray("searchEngines");
            if (!contains(list, "searchEngineName")) {
                JSONObject google = titles(new JSONObject()).put("searchEngineName", "google")
                        .put("channelNo", "").put("showIcon", false).put("iconUrl", "")
                        .put("searchUrl", "https://www.google.com/search?q={searchTerms}");
                list.put(google);
                scene.put("homepageSearchEngineCount", Math.max(list.length(), scene.optInt("homepageSearchEngineCount", 0)));
            }
        } else {
            JSONArray list = data.getJSONArray("searchEngines");
            if (!contains(list, "label")) list.put(titles(new JSONObject()).put("key", "eu_google")
                    .put("label", "google").put("searchUriDefault", "https://www.google.com/search?q={searchTerms}")
                    .put("searchUri", new JSONArray()).put("localOpen", new JSONArray())
                    .put("queryParameterKey", "q").put("extra", "eu-google").put("icon", "")
                    .put("iconHash", "google").put("isDefault", false).put("isRecommended", false));
        }
        return root.toString();
    }

    private static boolean contains(JSONArray list, String key) throws Exception {
        for (int i = 0; i < list.length(); i++) if ("google".equals(list.getJSONObject(i).optString(key))) return true;
        return false;
    }
    private static JSONObject titles(JSONObject object) throws Exception {
        for (String lang : new String[]{"zh_CN", "zh_TW", "en_US", "bo_CN", "ug_CN"}) object.put("title_" + lang, "Google");
        return object;
    }
}
