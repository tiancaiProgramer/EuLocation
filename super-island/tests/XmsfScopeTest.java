package com.ux5.hyperos3.elemeisland;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;

/** Real callbacks fail open when the host SDK has no hidden Application.attach API. */
public final class XmsfScopeTest {
    private static int logs;
    private static int checks;

    @SuppressWarnings("unchecked")
    private static <T> T param(Class<T> type, String name) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPackageName", "getProcessName" -> name;
                    case "isSystemServer" -> false;
                    default -> throw new AssertionError("Unexpected param access: " + method.getName());
                });
    }

    public static void main(String[] args) {
        XposedInterface framework = (XposedInterface) Proxy.newProxyInstance(
                XposedInterface.class.getClassLoader(), new Class<?>[]{XposedInterface.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("log")) { logs++; return null; }
                    throw new AssertionError("Unexpected framework call: " + method.getName());
                });
        Main module = new Main(framework, null);
        for (String process : new String[]{"com.xiaomi.xmsf", "com.xiaomi.xmsf:push",
                "com.xiaomi.xmsf:remote"}) {
            int before = logs;
            module.onModuleLoaded(param(XposedModuleInterface.ModuleLoadedParam.class, process));
            if (logs <= before) throw new AssertionError("Expected scoped entry or unavailable-API diagnostic");
            checks++;
        }
        for (int repeat = 0; repeat < 3; repeat++) {
            module.onPackageLoaded(param(XposedModuleInterface.PackageLoadedParam.class, "com.xiaomi.xmsf"));
            module.onPackageReady(param(XposedModuleInterface.PackageReadyParam.class, "com.xiaomi.xmsf"));
            checks += 2;
        }
        if (XmsfCompat.isProcess(null) || XmsfCompat.isProcess("com.xiaomi.xmsfkeeper")
                || XmsfCompat.isProcess("me.ele")) throw new AssertionError("Unexpected process match");
        checks += 3;
        System.out.println("XMSF callback and unavailable-API fail-open checks passed: " + checks);
    }
}
