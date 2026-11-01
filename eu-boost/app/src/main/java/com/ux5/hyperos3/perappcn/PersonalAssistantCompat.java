package com.ux5.hyperos3.perappcn;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;

/** Native travel entry/init only. Does not grant consent or change widget routing. */
final class PersonalAssistantCompat {
    private static final String TAG = "EUBoostTravel";
    private static final Object REGION_LOCK = new Object();
    private boolean installed;
    private Main module;

    synchronized void install(Main owner, ApplicationInfo info, ClassLoader loader) {
        if (installed) return;
        module = owner;
        try {
            if (!HookSupport.verified(info, "/product/priv-app/PersonalAssistant/PersonalAssistant.apk",
                    "de38a7447c59c9661de3b68828badfe756de763f85544b126640cb4deaa91e37")) {
                emit("Unverified PersonalAssistant; original behavior retained");
                return;
            }
            Class<?> app = Class.forName("com.miui.personalassistant.PAApplication", false, loader);
            Method init = app.getDeclaredMethod("a");
            if (init.getReturnType() != void.class) throw new NoSuchMethodException("init shape");
            Field international = HookSupport.field(Class.forName("miui.os.Build", true, loader), "IS_INTERNATIONAL_BUILD");
            module.deoptimize(init);
            module.hook(init).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                synchronized (REGION_LOCK) {
                    boolean original = international.getBoolean(null);
                    if (original && !HookSupport.writeBoolean(international, false)) return chain.proceed();
                    try { return chain.proceed(); }
                    finally {
                        if (original && !HookSupport.writeBoolean(international, true)) emit("ERROR: region restore failed");
                        report(loader, (Context) chain.getThisObject(), international.getBoolean(null));
                    }
                }
            });
            Class<?> fragment = Class.forName("com.miui.personalassistant.settings.PASettingFragment", false, loader);
            Method viewCreated = fragment.getDeclaredMethod("onViewCreated", View.class, Bundle.class);
            module.hook(viewCreated).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object preference = HookSupport.method(fragment, "d", CharSequence.class)
                            .invoke(chain.getThisObject(), "pref_key_travel");
                    if (preference != null) {
                        HookSupport.method(preference.getClass(), "J", boolean.class).invoke(preference, true);
                        emit("Travel entry visible; saved reminder/AI-consent choices untouched");
                    }
                } catch (Throwable error) { emit("Menu adaptation failed: " + error.getClass().getSimpleName()); }
                return result;
            });
            installed = true;
            emit("Native travel adapters installed; no network/consent/global-region override");
        } catch (Throwable error) { emit("Setup failed: " + error.getClass().getSimpleName()); }
    }
    private void report(ClassLoader loader, Context context, boolean restored) {
        try {
            Class<?> center = Class.forName("com.miui.personalassistant.travelservice.TravelCenter", true, loader);
            boolean travel = ((AtomicBoolean) HookSupport.field(center, "c").get(null)).get();
            Class<?> notification = Class.forName("com.umetrip.flightsdk.external.notification.NotificationContext", true, loader);
            Object nc = notification.getField("INSTANCE").get(null);
            boolean manager = notification.getMethod("getNotificationManager").invoke(nc) != null;
            Class<?> focus = Class.forName("com.umetrip.flightsdk.external.notification.FocusNotificationUtil", true, loader);
            Object eligible = focus.getMethod("checkFocusNotificationAvailable", Context.class)
                    .invoke(focus.getField("INSTANCE").get(null), context);
            emit("Native state: travelInitialized=" + travel + " notificationManager=" + manager
                    + " focusEligible=" + eligible + " internationalRestored=" + restored);
        } catch (Throwable error) { emit("State check failed: " + error.getClass().getSimpleName()); }
    }
    private void emit(String message) {
        Log.i(TAG, message);
        module.log(Log.INFO, TAG, message);
    }
}
