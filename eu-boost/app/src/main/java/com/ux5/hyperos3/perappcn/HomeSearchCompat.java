package com.ux5.hyperos3.perappcn;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;

/** Preserve the native swipe-up Search choice; never spoof Home's region. */
final class HomeSearchCompat {
    private boolean installed;
    private Method application;
    private String trustedSource;

    synchronized void install(Main owner, ApplicationInfo info, ClassLoader loader) {
        if (installed) return;
        try {
            if (!HookSupport.verified(info, "/product/priv-app/MiuiHome/MiuiHome.apk",
                    "2e7b507d9279498bb7a3f61115b38753778a9cae13ef61295dfa139c15057733")) {
                owner.log(Log.INFO, "EUBoostHomeSearch", "Unverified Home; unchanged"); return;
            }
            application = HookSupport.method(Class.forName("com.miui.home.launcher.Application", false, loader), "getInstance");
            Method reset = Class.forName("com.miui.home.launcher.GlobalSearchUtil", false, loader)
                    .getDeclaredMethod("updateslideUpGestureGlobalSearch");
            Method list = Class.forName("com.miui.home.launcher.search.SearchEdgeLayout", false, loader)
                    .getDeclaredMethod("useListSlidingSetting");
            owner.deoptimize(reset);
            owner.deoptimize(list);
            owner.hook(reset).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> searchAvailable() ? null : chain.proceed());
            owner.hook(list).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> searchAvailable() ? true : chain.proceed());
            installed = true;
            owner.log(Log.INFO, "EUBoostHomeSearch", "Native swipe-up Search choice preserved when original Search is enabled; gestures remain user-selected");
        } catch (Throwable error) {
            owner.log(Log.WARN, "EUBoostHomeSearch", "Home adapter unavailable: " + error.getClass().getSimpleName());
        }
    }

    private boolean searchAvailable() {
        try {
            Context context = (Context) application.invoke(null);
            ApplicationInfo info = context.getPackageManager().getApplicationInfo("com.android.quicksearchbox", 0);
            if (!info.enabled) return false;
            if (info.sourceDir.equals(trustedSource)) return true;
            if (SearchEngineCompat.trustedVariant(info) == null) return false;
            trustedSource = info.sourceDir;
            return true;
        } catch (Throwable ignored) { return false; }
    }
}
