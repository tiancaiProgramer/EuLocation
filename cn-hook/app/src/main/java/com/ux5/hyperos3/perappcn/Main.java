package com.ux5.hyperos3.perappcn;

import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * HyperOS3 Per-App CN — per-process CN / Xiaomi-EU build-detection override.
 *
 * <p>Loaded inside LSPosed-scoped processes. A runtime safety gate decides
 * whether the process may be spoofed: hooks only act when the currently loaded
 * package is a system app installed under a system partition
 * ({@code /system}, {@code /product}, {@code /system_ext}, {@code /vendor},
 * {@code /odm}, including their {@code app} / {@code priv-app} sub-trees).
 * Third-party / data apps no-op even if the user mistakenly scopes them, because
 * the LSPosed scope stays user-selectable and is never trusted on its own.</p>
 *
 * <p>Everything is fail-open: every step is {@code try/catch}-wrapped and hooks
 * are registered with {@code ExceptionMode.PROTECTIVE}, so a failed hook only
 * logs and the original behavior is kept.</p>
 */
public class Main extends XposedModule {

    private static final String TAG = "HyperOS3PerAppCN";
    private static final String XMSF_PACKAGE = "com.xiaomi.xmsf";
    private static final String XMSF_REGION_CLASS = "x4.a";

    private static final int GATE_UNKNOWN = 0;
    private static final int GATE_APPROVED = 1;
    private static final int GATE_DENIED = 2;

    private static final int FLAG_SYSTEM = 1;
    private static final int FLAG_UPDATED_SYSTEM_APP = 0x80;

    /** Fixed CN region properties, applied only after the safety gate passes. */
    private static final Map<String, String> CN_PROPS;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("ro.miui.region", "CN");
        m.put("ro.miui.build.region", "cn");
        m.put("ro.vendor.miui.region", "CN");
        m.put("ro.product.locale.region", "CN");
        m.put("persist.sys.country", "CN");
        CN_PROPS = Collections.unmodifiableMap(m);
    }

    /** Default constructor, used by frameworks that auto-attach the Xposed interface. */
    public Main() {
    }

    /** Two-arg constructor, used by frameworks that attach the interface themselves. */
    public Main(XposedInterface xposedInterface, XposedModuleInterface.ModuleLoadedParam moduleLoadedParam) {
        attachFramework(xposedInterface, () -> { });
    }

    /** Gate result: {@code null} = not yet determined, otherwise cached. */
    private volatile Boolean gateApproved;
    private volatile boolean miuiPatched;
    private volatile boolean xmsfHooksInstalled;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        try {
            String process = param.getProcessName();
            if (param.isSystemServer() || "system_server".equals(process)) {
                log(Log.WARN, TAG, "system_server: not hooking by design");
                return;
            }

            // XiaomiServiceFramework 6.2.x-G is a Global push build. Applying
            // the generic CN compatibility hooks makes its own region check
            // reject startup and can produce an empty registration host. Its
            // narrowly scoped fix is installed later, when the package class
            // loader is available in onPackageLoaded().
            if (isXmsfProcess(process)) {
                log(Log.INFO, TAG, "XMSF process detected; generic CN hooks skipped");
                return;
            }

            if ("com.android.settings".equals(process)
                    || (process != null && process.startsWith("com.android.settings:"))) {
                log(Log.INFO, TAG, "Settings: using narrow card-holder hooks, generic CN overrides skipped");
                return;
            }

            // Early safety-gate evaluation. If we can already tell this process is
            // not an approved system app, install nothing at all.
            int gate = evaluateApproval(process);
            if (gate == GATE_DENIED) {
                log(Log.INFO, TAG, "safety gate DENIED for process=" + process
                        + "; not a system app under a system location, no-op");
                return;
            }
            if (gate == GATE_APPROVED) {
                gateApproved = Boolean.TRUE;
            }

            installSystemPropertiesHooks();

            if (Boolean.TRUE.equals(gateApproved)) {
                patchMiuiBuild();
            }
            log(Log.INFO, TAG, "hooks installed, process=" + process
                    + ", gate=" + (gateApproved == null ? "pending" : gateApproved));
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onModuleLoaded failed", t);
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        try {
            if (!XMSF_PACKAGE.equals(param.getPackageName()) || !param.isFirstPackage()) {
                return;
            }

            ApplicationInfo ai = param.getApplicationInfo();
            if (!isApprovedSystemApp(ai)) {
                log(Log.WARN, TAG, "XMSF package rejected by system-app safety gate");
                return;
            }

            installXmsfGlobalHooks(param.getDefaultClassLoader(), ai);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "XMSF package callback failed; original behavior preserved", t);
        }
    }

    private static boolean isXmsfProcess(String process) {
        return process != null
                && (XMSF_PACKAGE.equals(process) || process.startsWith(XMSF_PACKAGE + ":"));
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if ("com.android.settings".equals(param.getPackageName()) && param.isFirstPackage()
                && isApprovedSystemApp(param.getApplicationInfo())) {
            SettingsCardHolder.install(this, param.getClassLoader());
        }
    }

    /**
     * Repairs the regional state used by XiaomiServiceFramework 6.2.67-G.
     *
     * <p>The G build itself maps {@code Global} to country code {@code SG} and
     * to the global registration/push hosts. On a CN-based ROM its cached
     * {@code mipush_region} can instead become {@code China}; the G build then
     * disables push registration and constructs {@code https:///pass/v2/register}.
     * Returning the pair selected by the app's own Global mapping keeps this
     * fix local to XMSF and leaves the rest of HyperOS in CN mode.</p>
     */
    private void installXmsfGlobalHooks(ClassLoader classLoader, ApplicationInfo ai) {
        synchronized (this) {
            if (xmsfHooksInstalled) {
                return;
            }

            try {
                Class<?> regionManager = Class.forName(XMSF_REGION_CLASS, false, classLoader);
                Method countryGetter = regionManager.getDeclaredMethod("a");
                Method regionGetter = regionManager.getDeclaredMethod("c");
                if (!String.class.equals(countryGetter.getReturnType())
                        || !String.class.equals(regionGetter.getReturnType())) {
                    log(Log.WARN, TAG, "XMSF signature gate failed; no hooks installed");
                    return;
                }

                countryGetter.setAccessible(true);
                regionGetter.setAccessible(true);
                hook(countryGetter).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> "SG");
                hook(regionGetter).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> "Global");
                xmsfHooksInstalled = true;
                log(Log.INFO, TAG, "XMSF Global push hooks installed: region=Global, country=SG"
                        + ", sourceDir=" + ai.sourceDir);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // Obfuscation/signature changes mean this is not the verified
                // 6.2.x-G layout. Fail open instead of guessing at a new build.
                log(Log.WARN, TAG, "XMSF compatibility signature not found; no-op", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "XMSF hook install failed; original behavior preserved", t);
            }
        }
    }

    /**
     * Resolves the current process's {@link ApplicationInfo} and decides whether
     * spoofing is allowed. Primary evidence: the installed location
     * ({@code sourceDir} / {@code publicSourceDir}) must be under a system
     * partition and the system flags must be set. Package identity is deliberately
     * not restricted: the user chooses the target Xiaomi system app in LSPosed,
     * while the gate prevents the hook from affecting ordinary /data apps.
     */
    private int evaluateApproval(String processName) {
        ApplicationInfo ai = resolveCurrentApplicationInfo();
        if (ai == null) {
            return GATE_UNKNOWN;
        }

        String packageName = ai.packageName != null ? ai.packageName : processName;
        String src = ai.sourceDir;
        String pub = ai.publicSourceDir;

        boolean systemFlag = (ai.flags & (FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP)) != 0;
        boolean systemPath = (src != null && isSystemPath(src))
                || (pub != null && isSystemPath(pub));

        log(Log.INFO, TAG, "gate eval package=" + packageName
                + " systemFlag=" + systemFlag
                + " systemPath=" + systemPath
                + " sourceDir=" + src
                + " publicSourceDir=" + pub
                + " flags=0x" + Integer.toHexString(ai.flags));

        boolean approved = isApprovedSystemApp(ai);
        return approved ? GATE_APPROVED : GATE_DENIED;
    }

    private static boolean isApprovedSystemApp(ApplicationInfo ai) {
        if (ai == null) {
            return false;
        }
        boolean systemFlag = (ai.flags & (FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP)) != 0;
        return systemFlag
                && ((ai.sourceDir != null && isSystemPath(ai.sourceDir))
                || (ai.publicSourceDir != null && isSystemPath(ai.publicSourceDir)));
    }

    private static boolean isSystemPath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.startsWith("/system/")
                || p.startsWith("/product/")
                || p.startsWith("/system_ext/")
                || p.startsWith("/vendor/")
                || p.startsWith("/odm/");
    }

    /**
     * Best-effort lookup of the ApplicationInfo of the package running in this
     * process. Returns {@code null} if the app is not attached yet (e.g. the
     * very first {@code SystemProperties.get} during class loading) — the caller
     * must treat that as {@link #GATE_UNKNOWN} and retry on the next call.
     */
    private ApplicationInfo resolveCurrentApplicationInfo() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method currentApplication = at.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object app = currentApplication.invoke(null);
            if (app != null) {
                Method getAi = app.getClass().getMethod("getApplicationInfo");
                getAi.setAccessible(true);
                Object ai = getAi.invoke(app);
                if (ai instanceof ApplicationInfo) {
                    return (ApplicationInfo) ai;
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method currentThread = at.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread != null) {
                Method getAi = thread.getClass().getMethod("getApplicationInfo");
                getAi.setAccessible(true);
                Object ai = getAi.invoke(thread);
                if (ai instanceof ApplicationInfo) {
                    return (ApplicationInfo) ai;
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return null;
    }

    private void installSystemPropertiesHooks() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get1 = sp.getDeclaredMethod("get", String.class);
            get1.setAccessible(true);
            hook(get1).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::spoofProp);
            log(Log.INFO, TAG, "hooked SystemProperties.get(String)");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "cannot hook SystemProperties.get(String)", t);
        }
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get2 = sp.getDeclaredMethod("get", String.class, String.class);
            get2.setAccessible(true);
            hook(get2).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::spoofProp);
            log(Log.INFO, TAG, "hooked SystemProperties.get(String,String)");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "cannot hook SystemProperties.get(String,String)", t);
        }
    }

    private Object spoofProp(XposedInterface.Chain chain) throws Throwable {
        // Resolve the safety gate lazily (app may not be attached yet at load).
        if (gateApproved == null) {
            String process = "?";
            try {
                process = String.valueOf(chain.getExecutable());
            } catch (Throwable ignored) {
                // ignore
            }
            int gate = evaluateApproval(process);
            if (gate == GATE_APPROVED) {
                gateApproved = Boolean.TRUE;
                patchMiuiBuild();
            } else if (gate == GATE_DENIED) {
                gateApproved = Boolean.FALSE;
                log(Log.WARN, TAG, "safety gate DENIED at runtime, spoofing disabled");
            }
        }
        if (!Boolean.TRUE.equals(gateApproved)) {
            return chain.proceed();
        }

        try {
            Object arg0 = chain.getArg(0);
            if (arg0 instanceof String) {
                String key = (String) arg0;
                if ("ro.product.mod_device".equals(key)) {
                    return dynamicModDevice(chain);
                }
                String value = CN_PROPS.get(key);
                if (value != null) {
                    return value;
                }
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "spoofProp failed, proceeding with original", t);
        }
        return chain.proceed();
    }

    /**
     * Reads the original {@code ro.product.mod_device} and strips a trailing
     * XiaomiEU / global suffix to obtain the base CN device code. Dynamic:
     * never hardcodes a device name. If the value has no such suffix (e.g. it is
     * already a CN value) or is missing, it is passed through unchanged.
     */
    private Object dynamicModDevice(XposedInterface.Chain chain) throws Throwable {
        Object original = chain.proceed();
        if (!(original instanceof String)) {
            return original;
        }
        String value = (String) original;
        String stripped = stripXiaomiEuSuffix(value);
        if (stripped != null && !stripped.equals(value)) {
            log(Log.INFO, TAG, "ro.product.mod_device '" + value + "' -> '" + stripped + "'");
            return stripped;
        }
        return value;
    }

    private static String stripXiaomiEuSuffix(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        String[] suffixes = { "_xiaomieu_global", "_xiaomieu", "_global" };
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return null;
    }

    private void patchMiuiBuild() {
        synchronized (this) {
            if (miuiPatched) {
                return;
            }
            miuiPatched = true;
        }
        try {
            Class<?> miuiBuild = Class.forName("miui.os.Build");
            setStaticBoolean(miuiBuild, "IS_GLOBAL_BUILD", false);
            setStaticBoolean(miuiBuild, "IS_INTERNATIONAL_BUILD", false);
            setStaticBoolean(miuiBuild, "IS_CN_BUILD", true);
            hookStringGetter(miuiBuild, "getRegion", "CN");
            hookStringGetter(miuiBuild, "getCustVariant", "cn");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "miui.os.Build patch failed, continuing without it", t);
        }
    }

    /**
     * Sets a static boolean field. Tries plain reflection first (works for
     * non-final fields), then falls back to sun.misc.Unsafe (works for many
     * static-final fields). Fail-open: if both fail, the field is left as is.
     */
    private static void setStaticBoolean(Class<?> clazz, String name, boolean value) {
        boolean reflected = false;
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(null, value);
            reflected = true;
        } catch (Throwable ignored) {
            reflected = false;
        }
        if (reflected) {
            return;
        }
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method baseMethod = unsafeClass.getDeclaredMethod("staticFieldBase", Field.class);
            Method offset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);
            Method putBoolean = unsafeClass.getDeclaredMethod("putBoolean", Object.class, long.class, boolean.class);
            Object base = baseMethod.invoke(unsafe, f);
            long off = (Long) offset.invoke(unsafe, f);
            putBoolean.invoke(unsafe, base, off, value);
        } catch (Throwable ignored) {
            // fail-open: field could not be modified
        }
    }

    /**
     * Hooks a String-returning static getter on miui.os.Build so it returns the
     * given value. Missing methods are ignored; un-hookable methods are logged
     * and skipped (fail-open).
     */
    private void hookStringGetter(Class<?> clazz, String name, String value) {
        try {
            Method m = clazz.getDeclaredMethod(name);
            if (!String.class.equals(m.getReturnType())) {
                return;
            }
            m.setAccessible(true);
            hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> value);
            log(Log.INFO, TAG, "hooked " + clazz.getName() + "." + name + "() -> " + value);
        } catch (NoSuchMethodException ignored) {
            // not present on this build
        } catch (Throwable t) {
            log(Log.WARN, TAG, "cannot hook " + clazz.getName() + "." + name + "()", t);
        }
    }
}
