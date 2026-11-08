package com.ux5.hyperos3.elemeisland;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;

/** Restore the verified Global SDK's China-region enable gate without changing region or IDs. */
final class XmsfCompat {
    static final String PACKAGE = "com.xiaomi.xmsf";
    private static boolean attached, hooked;
    private static int notes;

    private XmsfCompat() { }

    static boolean isProcess(String process) {
        return process != null
                && (PACKAGE.equals(process) || process.startsWith(PACKAGE + ":"));
    }
    static synchronized void install(Main owner) {
        if (attached) return;
        attached = true;
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            owner.hook(attach).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(c -> {
                Object result = c.proceed();
                try { bind(owner, (Context) c.getArg(0)); }
                catch (Throwable error) { owner.log(Log.WARN, "ElemeIslandAdapter", "XMSF compatibility skipped: " + error.getClass().getSimpleName()); }
                return result;
            });
        } catch (Throwable error) { owner.log(Log.WARN, "ElemeIslandAdapter", "XMSF attach unavailable: " + error.getClass().getSimpleName()); }
    }
    private static synchronized void bind(Main owner, Context context) throws Exception {
        if (hooked || !PACKAGE.equals(context.getPackageName())) return;
        PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
        if (!"6.2.67-G".equals(info.versionName) || info.getLongVersionCode() != 60002067L) {
            owner.log(Log.INFO, "ElemeIslandAdapter", "XMSF version unverified; original enable policy retained"); return;
        }
        ClassLoader loader = context.getClassLoader();
        Class<?> service = Class.forName("com.xiaomi.push.service.XMPushService", false, loader);
        Class<?> region = Class.forName("x4.a", false, loader), platform = Class.forName("n3.d", false, loader);
        Method enable = service.getDeclaredMethod("o"), miui = platform.getDeclaredMethod("p");
        Method regionInstance = region.getDeclaredMethod("b", Context.class), regionValue = region.getDeclaredMethod("c");
        if (enable.getReturnType() != boolean.class || miui.getReturnType() != boolean.class || regionValue.getReturnType() != String.class) return;
        owner.deoptimize(enable);
        owner.hook(enable).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(c -> {
            Object result = c.proceed();
            if (Boolean.TRUE.equals(result)) return result;
            try {
                Context host = (Context) c.getThisObject();
                boolean isMiui = Boolean.TRUE.equals(miui.invoke(null));
                String stored = (String) regionValue.invoke(regionInstance.invoke(null, host));
                boolean allow = XmsfPolicy.allow(false, host.getPackageName(), info.versionName, info.getLongVersionCode(), isMiui, stored);
                if (allow) {
                    if (notes++ < 4) owner.log(Log.INFO, "ElemeIslandAdapter", "XMSF 6.2.67-G China enable gate restored; region and registration data retained");
                    return true;
                }
            } catch (Throwable error) { owner.log(Log.WARN, "ElemeIslandAdapter", "XMSF gate observation failed; original result retained"); }
            return result;
        });
        hooked = true;
        owner.log(Log.INFO, "ElemeIslandAdapter", "XMSF 6.2.67-G regional enable compatibility installed");
        Class<?> transport = Class.forName("x4.b1", false, loader), account = Class.forName("x4.n", false, loader);
        Method chat = transport.getDeclaredMethod("a"), registration = account.getDeclaredMethod("h", Context.class, boolean.class);
        if (chat.getReturnType() != String.class || registration.getReturnType() != String.class) return;
        owner.deoptimize(chat); owner.deoptimize(registration);
        owner.hook(chat).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(c -> {
            Object result = c.proceed();
            try {
                boolean verified = XmsfPolicy.allow(false, context.getPackageName(), info.versionName, info.getLongVersionCode(),
                        Boolean.TRUE.equals(miui.invoke(null)), (String) regionValue.invoke(regionInstance.invoke(null, context)));
                return XmsfPolicy.chatHost((String) result, verified);
            } catch (Throwable ignored) { return result; }
        });
        owner.hook(registration).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(c -> {
            Object result = c.proceed();
            try {
                boolean verified = XmsfPolicy.allow(false, context.getPackageName(), info.versionName, info.getLongVersionCode(),
                        Boolean.TRUE.equals(miui.invoke(null)), (String) regionValue.invoke(regionInstance.invoke(null, context)));
                return XmsfPolicy.registrationUrl((String) result, (Boolean) c.getArg(1), verified);
            } catch (Throwable ignored) { return result; }
        });
        owner.log(Log.INFO, "ElemeIslandAdapter", "XMSF 6.2.67-G China transport and native account-registration endpoints installed");
    }
}
