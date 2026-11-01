package com.ux5.hyperos3.perappcn;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

/** Launch only the injected Google search URL in the user's current browser. */
final class GoogleBrowserRoute {
    static boolean accepts(String value) {
        if (value == null) return false;
        try {
            Uri uri = Uri.parse(value);
            return "https".equals(uri.getScheme()) && "www.google.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && "/search".equals(uri.getPath()) && uri.getQueryParameter("q") != null;
        } catch (Exception ignored) { return false; }
    }

    static Intent browserIntent(Context context, String url) {
        if (!accepts(url)) throw new IllegalArgumentException("Not the injected Google search URL");
        Intent result = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        result.addCategory(Intent.CATEGORY_BROWSABLE);
        result.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Resolve an ordinary HTTPS page to avoid Google App link interception.
        // This is a local package-manager query, not a network request.
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"));
        probe.addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo browser = context.getPackageManager().resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        if (browser != null && browser.activityInfo != null && browser.activityInfo.enabled
                && !"android".equals(browser.activityInfo.packageName)) {
            Intent pinned = new Intent(result).setPackage(browser.activityInfo.packageName);
            if (context.getPackageManager().resolveActivity(pinned, PackageManager.MATCH_DEFAULT_ONLY) != null)
                return pinned;
        }
        return result;
    }

    private GoogleBrowserRoute() { }
}
