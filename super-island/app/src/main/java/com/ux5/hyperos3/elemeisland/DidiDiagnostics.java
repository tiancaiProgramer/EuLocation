package com.ux5.hyperos3.elemeisland;

import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.libxposed.api.XposedInterface;

/** Bounded metadata-only observations; never changes native support or request results. */
final class DidiDiagnostics {
    private final AtomicInteger count = new AtomicInteger();

    void note(Main owner, String text) {
        if (count.getAndIncrement() < 40) owner.log(Log.INFO, "ElemeIslandAdapter", "DidiDiag " + text);
    }

    void install(Main owner, ClassLoader loader, Class<?> window, Class<?> data, DidiProfile profile) {
        if (profile != DidiProfile.V8014) return;
        try {
            Method support = window.getDeclaredMethod("isSupport");
            if (support.getReturnType() != boolean.class) return;
            owner.deoptimize(support);
            owner.hook(support).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                note(owner, "nativeSupport=" + Boolean.TRUE.equals(result));
                return result;
            });
            Class<?> manager = Class.forName("com.didi.live.window.LiveWindowManage", false, loader);
            Method gate = manager.getDeclaredMethod("g", String.class, String.class, String.class, String.class);
            Method current = manager.getDeclaredMethod("e");
            current.setAccessible(true);
            if (gate.getReturnType() != boolean.class) return;
            owner.deoptimize(gate);
            owner.hook(gate).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object active = current.invoke(chain.getThisObject());
                    note(owner, "preflight=" + Boolean.TRUE.equals(result) + " window="
                            + (active == null ? "null" : active.getClass().getName()));
                } catch (Throwable ignored) { note(owner, "preflight observed; window unavailable"); }
                return result;
            });
            for (Method method : manager.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                // Avoid an optimized caller bypassing c(int), which is a constant-true native method.
                if (method.getName().equals("a") && Modifier.isStatic(method.getModifiers())
                        && p.length == 3 && p[0] == manager && p[2] == int.class) owner.deoptimize(method);
                if (!method.getName().equals("k") || p.length != 4 || p[0] != data
                        || p[2] != String.class || p[3] != String.class || method.getReturnType() != void.class) continue;
                java.lang.reflect.Field status = data.getDeclaredField("a");
                java.lang.reflect.Field body = data.getDeclaredField("c");
                status.setAccessible(true); body.setAccessible(true);
                owner.deoptimize(method);
                owner.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    try {
                        Object response = chain.getArg(0);
                        Object payload = response == null ? null : body.get(response);
                        note(owner, "responseCode=" + (response == null ? "null" : status.getInt(response))
                                + " hasDataMap=" + (payload instanceof Map && !((Map<?, ?>) payload).isEmpty()));
                    } catch (Throwable ignored) { note(owner, "response metadata unavailable"); }
                    return chain.proceed();
                });
            }
            note(owner, "support/preflight/response observers installed; limit=40");
        } catch (Throwable error) { note(owner, "observers unavailable: " + error.getClass().getSimpleName()); }
    }
}
