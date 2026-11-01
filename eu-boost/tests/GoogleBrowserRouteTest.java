package com.ux5.hyperos3.perappcn;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public final class GoogleBrowserRouteTest {
    public static void main(String[] args) throws Exception {
        String valid = "https://www.google.com/search?q=%E5%BD%95%E9%9F%B3%E6%9C%BA%20a%2Bb%26c";
        check(GoogleBrowserRoute.accepts(valid), "encoded query rejected");
        for (String rejected : new String[]{null, "https://www.baidu.com/s?q=x",
                "https://www.google.com.evil.example/search?q=x", "http://www.google.com/search?q=x",
                "https://www.google.com:444/search?q=x", "https://user@www.google.com/search?q=x",
                "https://www.google.com/search", "https://www.google.com/other?q=x",
                "intent://www.google.com/search?q=x", "com.android.settings"})
            check(!GoogleBrowserRoute.accepts(rejected), "unexpected route accepted");
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        Object thread = Class.forName("android.app.ActivityThread").getDeclaredMethod("systemMain").invoke(null);
        Context context = (Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
        Intent intent = GoogleBrowserRoute.browserIntent(context, valid);
        check(valid.equals(intent.getDataString()), "query changed/double encoded");
        check(Intent.ACTION_VIEW.equals(intent.getAction()), "wrong action");
        check(intent.hasCategory(Intent.CATEGORY_BROWSABLE), "browser category absent");
        check(!"com.android.browser".equals(intent.getPackage()), "forced Xiaomi browser");
        check(context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null, "no handler");
        System.out.println("GOOGLE_BROWSER_ROUTE_PASS: strict URL boundary, encoded query unchanged, handler=" + intent.getPackage());
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
