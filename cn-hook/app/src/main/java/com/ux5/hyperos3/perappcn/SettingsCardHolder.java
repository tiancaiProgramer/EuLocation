package com.ux5.hyperos3.perappcn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import io.github.libxposed.api.XposedInterface;

/** Capability-gated EU Settings adaptation. Never changes global region or payment defaults. */
final class SettingsCardHolder {
    private static final String TAG = "EUCardHolder";
    private static final String KEY = "mi_wallet_settings";
    private static final String APK = "/product/app/MITSMClient/MITSMClient.apk";
    private static final String HASH = "c79df91c96734bcd0085ec19e74e55abce7be55b7a7a8429b18ec73cdb4332c5";
    private static boolean installed;
    private static boolean trusted;
    private static long checkedAt;
    private static boolean capability;
    private static boolean logged;

    static synchronized void install(Main module, ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> controller = Class.forName("com.android.settings.personal.MiWalletController", false, loader);
            Class<?> utils = Class.forName("com.android.settings.MiuiUtils", false, loader);
            Method available = controller.getDeclaredMethod("getAvailabilityStatus");
            module.hook(available).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Context context = contextOf(chain.getThisObject());
                if (allowed(context)) {
                    if (!logged) {
                        module.log(Log.INFO, TAG, "Trusted TSM feature 0x400 verified; native preference available");
                        logged = true;
                    }
                    return 0;
                }
                return chain.proceed();
            });
            module.hook(utils.getDeclaredMethod("isSupportCardHolder", Context.class))
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> allowed((Context) chain.getArg(0)) ? true : chain.proceed());

            for (Method method : controller.getDeclaredMethods()) {
                String name = method.getName();
                if ("handlePreferenceTreeClick".equals(name) && method.getParameterCount() == 1) {
                    module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object preference = chain.getArg(0);
                        Context context = contextOf(chain.getThisObject());
                        if (preference != null && KEY.equals(preference.getClass().getMethod("getKey").invoke(preference))
                                && allowed(context)) {
                            Activity activity = (Activity) field(chain.getThisObject(), "mActivity");
                            if (activity != null) {
                                Intent intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("tsmclient://card?type=all&action=com.miui.tsmclient.action.CARD_HOLDER"));
                                intent.setPackage("com.miui.tsmclient");
                                if (intent.resolveActivity(context.getPackageManager()) != null) {
                                    activity.startActivity(intent);
                                    module.log(Log.INFO, TAG, "Native Settings card-holder click handled by TSM");
                                    return true;
                                }
                            }
                        }
                        return chain.proceed();
                    });
                }
                if ("displayPreference".equals(name) || "updateState".equals(name)) {
                    module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        Context context = contextOf(chain.getThisObject());
                        if (allowed(context)) {
                            Object preference = chain.getArg(0);
                            if ("displayPreference".equals(chain.getExecutable().getName())) {
                                preference = preference.getClass().getMethod("findPreference", CharSequence.class)
                                        .invoke(preference, KEY);
                            }
                            int title = context.getResources().getIdentifier("card_holder_header_title", "string", "com.android.settings");
                            if (preference != null && title != 0) {
                                preference.getClass().getMethod("setTitle", CharSequence.class)
                                        .invoke(preference, context.getString(title));
                            }
                        }
                        return result;
                    });
                }
                // Recompile only these UI callers, not every Settings method.
                if ("displayPreference".equals(name) || "updateState".equals(name)
                        || "handlePreferenceTreeClick".equals(name)) module.deoptimize(method);
            }
            Class<?> baseController = Class.forName("com.android.settings.core.BasePreferenceController", false, loader);
            Method isAvailable = baseController.getDeclaredMethod("isAvailable");
            if (!java.lang.reflect.Modifier.isAbstract(isAvailable.getModifiers())) module.deoptimize(isAvailable);
            installed = true;
            module.log(Log.INFO, TAG, "v0.7 narrow Settings hooks installed; generic CN path excluded");
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "Settings hook setup incomplete; original behavior retained where not hooked", error);
        }
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static Context contextOf(Object controller) {
        try { return (Context) field(controller, "mContext"); }
        catch (Exception ignored) { return null; }
    }

    private static String property(String key) throws Exception {
        return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
    }

    private static synchronized boolean allowed(Context context) {
        try {
            if (context == null || !"com.android.settings".equals(context.getPackageName())
                    || !PlatformProfile.matches(android.os.Build.VERSION.SDK_INT,
                            property("ro.product.cpu.abi"), property("ro.build.version.incremental"),
                            property("ro.product.mod_device"))
                    || !context.getSharedPreferences("ex_func", 0).getBoolean("ex_func_wallet", true)) return false;
            if (!trusted) {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo("com.miui.tsmclient", 0);
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || !APK.equals(info.sourceDir)) return false;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (FileInputStream input = new FileInputStream(APK)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
                }
                StringBuilder hex = new StringBuilder();
                for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                if (!HASH.equals(hex.toString())) return false;
                trusted = true;
            }
            long now = SystemClock.elapsedRealtime();
            if (checkedAt == 0 || now - checkedAt > 5000) {
                Bundle features = context.getContentResolver().call(
                        Uri.parse("content://com.miui.tsmclient.provider.feature"), "feature", null, null);
                capability = features != null && (features.getInt("feature_value", 0) & 1024) != 0;
                checkedAt = now;
            }
            return capability;
        } catch (Exception ignored) { return false; }
    }
}
