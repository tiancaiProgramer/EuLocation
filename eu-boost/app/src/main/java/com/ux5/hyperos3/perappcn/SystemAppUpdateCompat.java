package com.ux5.hyperos3.perappcn;

import android.content.pm.ApplicationInfo;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;

/** Installer parser-only virtual property. Never writes global/persistent properties. */
final class SystemAppUpdateCompat {
    private static final String TAG = "EUBoostInstaller";
    private static final String KEY = "persist.sys.allow_update_systemapp";
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    private boolean installed;
    private Main module;

    synchronized void install(Main owner, ApplicationInfo info, ClassLoader loader) {
        if (installed) return;
        module = owner;
        try {
            if (!HookSupport.verified(info, "/product/priv-app/MiuiPackageInstaller/MiuiPackageInstaller.apk",
                    "6d00f8e114cb14e080257b7d267587e0a8942861b40900a277f3217e0773d414")) {
                emit("Unverified installer; original behavior retained");
                return;
            }
            Class<?> parser = Class.forName("n2.c", false, loader);
            Class<?> apkInfo = Class.forName("com.miui.packageInstaller.model.ApkInfo", false, loader);
            Method parse = parser.getDeclaredMethod("b", String.class, apkInfo);
            if (parse.getReturnType() != void.class) throw new NoSuchMethodException("parser shape");
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method getBoolean = properties.getDeclaredMethod("getBoolean", String.class, boolean.class);
            getBoolean.setAccessible(true);
            module.deoptimize(parse);
            module.hook(parse).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                depth.set(depth.get() + 1);
                try { return chain.proceed(); }
                finally {
                    int remaining = depth.get() - 1;
                    if (remaining == 0) depth.remove(); else depth.set(remaining);
                }
            });
            module.hook(getBoolean).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (KEY.equals(chain.getArg(0)) && depth.get() > 0) {
                    Object global = chain.proceed();
                    emit("Parser-local allow applied; originalGlobal=" + global + "; no property written");
                    return true;
                }
                return chain.proceed();
            });
            installed = true;
            emit("Installer compatibility installed; parser only, system signature checks untouched");
            // A read-only same-process self-test, without parsing or installing any APK.
            Object before = getBoolean.invoke(null, KEY, false);
            Object inside;
            depth.set(depth.get() + 1);
            try { inside = getBoolean.invoke(null, KEY, false); }
            finally { depth.remove(); }
            Object after = getBoolean.invoke(null, KEY, false);
            emit("Local self-test: outsideBefore=" + before + " inside=" + inside + " outsideAfter=" + after);
        } catch (Throwable error) { emit("Setup/self-test failed: " + error.getClass().getSimpleName()); }
    }
    private void emit(String message) {
        Log.i(TAG, message);
        module.log(Log.INFO, TAG, message);
    }
}
