package com.ux5.hyperos3.perappcn;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.libxposed.api.XposedInterface;

/** Extends native engine configuration in memory, without changing APK/signature. */
final class SearchEngineCompat {
    private boolean installed;
    static final String PHONE = "c5c818831537b1edae9e50c8f2fb733068a8aeb9b07bfc2f000271d6f4efbc57";
    static final String FOLD = "f988c9720c97aac9ae7e747879060c7e92cb5eb81c7f414e60373f2fe4c7d422";

    static String trustedVariant(ApplicationInfo info) throws Exception {
        if (info == null || !"com.android.quicksearchbox".equals(info.packageName)
                || android.os.Build.VERSION.SDK_INT != 36) return null;
        String path = info.sourceDir;
        if (path == null || !(path.startsWith("/data/app/") || path.startsWith("/product/")
                || path.startsWith("/system/") || path.startsWith("/system_ext/"))) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(path)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        if (PHONE.contentEquals(hex)) return "phone";
        if (FOLD.contentEquals(hex)) return "fold";
        return null;
    }

    synchronized void install(Main owner, ApplicationInfo info, ClassLoader loader) {
        if (installed) return;
        try {
            String variant = trustedVariant(info);
            if (variant == null) { owner.log(Log.INFO, "EUBoostSearch", "Unverified Search; unchanged"); return; }
            boolean phone = "phone".equals(variant);
            Class<?> base = Class.forName(phone ? "G2.e0"
                    : "com.android.quicksearchbox.xiaomi.BasicVersionableData", false, loader);
            Method stream = base.getDeclaredMethod(phone ? "s" : "getInputStream", Context.class, String.class);
            Method filename = HookSupport.method(base, phone ? "o" : "getDataFileName");
            owner.deoptimize(stream);
            owner.hook(stream).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                InputStream result = (InputStream) chain.proceed();
                try {
                    String name = (String) filename.invoke(chain.getThisObject());
                    String requested = (String) chain.getArg(1);
                    if (result == null || !(phone ? "searchengineNew.json" : "searchengine.json").equals(name)
                            || (requested != null && !requested.isEmpty() && !requested.equals(name))) return result;
                    return extendStream(result, phone);
                } catch (Throwable ignored) { return result; }
            });
            installBrowserRoute(owner, loader, phone);
            installed = true;
            owner.log(Log.INFO, "EUBoostSearch", "Google engine adapter installed: " + variant + "; native default retained");
        } catch (Throwable error) {
            owner.log(Log.WARN, "EUBoostSearch", "Search adapter unavailable: " + error.getClass().getSimpleName());
        }
    }

    private void installBrowserRoute(Main owner, ClassLoader loader, boolean phone) {
        try {
            Class<?> util = Class.forName(phone ? "y2.Q0" : "com.android.quicksearchbox.util.Util", false, loader);
            Method dispatch = phone
                    ? util.getDeclaredMethod("H", Context.class, String.class, String.class)
                    : util.getDeclaredMethod("startBrowserActivity", Context.class, String.class);
            if (dispatch.getReturnType() != boolean.class) throw new NoSuchMethodException("browser return type");
            owner.deoptimize(dispatch);
            owner.hook(dispatch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                String url = (String) chain.getArg(1);
                if (!GoogleBrowserRoute.accepts(url)) return chain.proceed();
                try {
                    Context context = (Context) chain.getArg(0);
                    context.startActivity(GoogleBrowserRoute.browserIntent(context, url));
                    owner.log(Log.INFO, "EUBoostSearch", "Google search dispatched to current browser; no URL/query logged");
                    return true;
                } catch (Exception error) {
                    owner.log(Log.WARN, "EUBoostSearch", "Browser dispatch failed: " + error.getClass().getSimpleName());
                    return chain.proceed();
                }
            });
            owner.log(Log.INFO, "EUBoostSearch", "Google browser route installed: " + (phone ? "phone" : "fold"));
        } catch (Throwable error) {
            owner.log(Log.WARN, "EUBoostSearch", "Browser route unavailable: " + error.getClass().getSimpleName());
        }
    }

    // Bound allocations and reconstruct the original stream on parse/size failure.
    static InputStream extendStream(InputStream source, boolean phone) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int read;
        try {
            while (bytes.size() <= 2 * 1024 * 1024 && (read = source.read(buffer)) != -1) bytes.write(buffer, 0, read);
        } catch (Exception ignored) {
            return new SequenceInputStream(new ByteArrayInputStream(bytes.toByteArray()), source);
        }
        byte[] original = bytes.toByteArray();
        if (bytes.size() > 2 * 1024 * 1024)
            return new SequenceInputStream(new ByteArrayInputStream(original), source);
        byte[] replacement = original;
        try { replacement = SearchEngineConfig.extend(new String(original, StandardCharsets.UTF_8), phone).getBytes(StandardCharsets.UTF_8); }
        catch (Exception ignored) { /* Return identical bytes for an unknown schema. */ }
        try { source.close(); } catch (Exception ignored) { }
        return new ByteArrayInputStream(replacement);
    }

}
