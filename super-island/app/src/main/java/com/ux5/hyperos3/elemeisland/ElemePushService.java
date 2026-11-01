package com.ux5.hyperos3.elemeisland;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;

/** Starts the app's declared MiPush service from its own foreground activity. */
final class ElemePushService {
    private static final String PACKAGE = "me.ele";
    private static final String SERVICE = "com.xiaomi.push.service.XMPushService";
    private static final String TAG = "ElemeNativePush";
    private boolean installed;
    private boolean requested;
    private long lastAttempt = -30_000;

    synchronized void install(Main module) {
        if (installed) return;
        try {
            Method resume = Instrumentation.class.getDeclaredMethod("callActivityOnResume", Activity.class);
            if (resume.getReturnType() != void.class) return;
            module.hook(resume).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            if (chain.getArg(0) instanceof Activity activity) start(module, activity);
                        } catch (Throwable error) {
                            emit(module, "Foreground push start skipped: " + error.getClass().getSimpleName());
                        }
                        return result;
                    });
            installed = true;
            emit(module, "Foreground native push start installed; no timers or background keep-alive");
        } catch (Throwable error) {
            emit(module, "Foreground entry unavailable: " + error.getClass().getSimpleName());
        }
    }

    private synchronized void start(Main module, Activity activity) throws Exception {
        if (requested || !PACKAGE.equals(activity.getPackageName())) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAttempt < 30_000) return;
        lastAttempt = now;
        ComponentName component = new ComponentName(PACKAGE, SERVICE);
        PackageManager pm = activity.getPackageManager();
        int state = pm.getComponentEnabledSetting(component);
        if (state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                && state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return;
        ServiceInfo info = pm.getServiceInfo(component, 0);
        if (!info.enabled || !PACKAGE.equals(info.packageName)
                || !(PACKAGE + ":pushservice").equals(info.processName)) return;
        ComponentName started = activity.startService(new Intent().setComponent(component));
        if (component.equals(started)) {
            requested = true;
            emit(module, "Native MiPush service start requested from foreground; no order or registration data written");
        } else {
            emit(module, "Native MiPush service did not start; original app behavior retained");
        }
    }

    private static void emit(Main module, String text) {
        try { module.log(Log.INFO, TAG, text); } catch (Throwable ignored) { }
    }
}
