package com.ux5.hyperos3.elemeisland;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import org.json.JSONObject;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Narrow compatibility adapter for live-service islands on HyperOS 3 Xiaomi.eu.
 *
 * <p>Retains the verified Ele.me route and separate Didi adapter. Owns the
 * version-gated XMSF China enable compatibility. Device region and push
 * registration data are preserved.</p>
 *
 * <p>The hook is fail-open and signature-gated. If the verified SDK layout is
 * absent or changes, the original behavior is preserved.</p>
 */
public final class Main extends XposedModule {

    private static final String TAG = "ElemeIslandAdapter";
    private static final String TARGET_PACKAGE = "me.ele";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String SYSTEMUI_PLUGIN_PACKAGE = "miui.systemui.plugin";
    private static final String ROUTER_CLASS = "com.xiaomi.mipush.sdk.ao";
    private static final String NOTIFICATION_SETTINGS_CLASS =
            "com.miui.systemui.notification.NotificationSettingsManager";
    private static final String PLUGIN_NOTIFICATION_SETTINGS_CLASS =
            "miui.systemui.notification.NotificationSettingsManager";
    private static final String FOCUS_NOTIFICATION_CONTROLLER_CLASS =
            "miui.systemui.notification.focus.FocusNotificationController";
    private static final String FOCUS_PLUGIN_IMPL_CLASS =
            "miui.systemui.notification.FocusNotificationPluginImpl";
    private static final String FOCUS_PARAM = "miui.focus.param";
    private static final String AUTO_FOCUS_MARKER =
            "com.ux5.hyperos3.elemeisland.autofocus";

    /** Only the two live-service packages currently being validated. */
    private static final Set<String> FOCUS_PERMISSION_PACKAGES = Set.of(
            "me.ele",
            "com.sdu.didi.psnger"
    );

    private volatile boolean routerInstalled;
    private volatile boolean notificationBuilderHookInstalled;
    private volatile boolean notificationManagerHookInstalled;
    private volatile boolean notificationBinderHookInstalled;
    private volatile boolean focusPermissionInstalled;
    private volatile boolean pluginFocusPermissionInstalled;
    private volatile boolean focusPayloadInstalled;
    private volatile boolean focusPluginPostedInstalled;
    private volatile boolean systemUiListenerInstalled;
    private volatile boolean didiLifecycleInstalled;
    private volatile String clientPackage = TARGET_PACKAGE;
    private volatile boolean elemeMainProcess;
    private final DidiDiagnostics didiDiagnostics = new DidiDiagnostics();
    private final DidiAdapter didi = new DidiAdapter(message -> log(Log.INFO, TAG, message));
    private final ElemePushService nativePush = new ElemePushService();

    public Main() {
    }

    public Main(XposedInterface xposedInterface,
                XposedModuleInterface.ModuleLoadedParam moduleLoadedParam) {
        attachFramework(xposedInterface, () -> { });
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        String process = param.getProcessName();
        elemeMainProcess = TARGET_PACKAGE.equals(process);
        if (!param.isSystemServer() && XmsfCompat.isProcess(process)) {
            XmsfCompat.install(this);
            log(Log.INFO, TAG, "XMSF scoped compatibility observer loaded");
            return;
        }
        if (param.isSystemServer() || process == null
                || !(TARGET_PACKAGE.equals(process)
                || process.startsWith(TARGET_PACKAGE + ":")
                || DidiPolicy.PACKAGE.equals(process)
                || process.startsWith(DidiPolicy.PACKAGE + ":")
                || SYSTEMUI_PACKAGE.equals(process))) {
            log(Log.INFO, TAG, "out-of-scope process; no-op: " + process);
        } else {
            log(Log.INFO, TAG, "in-scope process loaded: " + process);
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        try {
            String packageName = param.getPackageName();
            if (XmsfCompat.PACKAGE.equals(packageName)) {
                return;
            }
            if (SYSTEMUI_PACKAGE.equals(packageName)) {
                installSystemUiListenerHook(param.getDefaultClassLoader());
                installFocusPermissionHook(param.getDefaultClassLoader(),
                        param.getApplicationInfo());
                installFocusPluginPostedHook(param.getDefaultClassLoader(),
                        param.getApplicationInfo());
                return;
            }

            if (SYSTEMUI_PLUGIN_PACKAGE.equals(packageName)) {
                installPluginFocusPermissionHook(param.getDefaultClassLoader(),
                        param.getApplicationInfo());
                installFocusPayloadHook(param.getDefaultClassLoader(),
                        param.getApplicationInfo());
                installFocusPluginPostedHook(param.getDefaultClassLoader(),
                        param.getApplicationInfo());
                return;
            }

            if (!TARGET_PACKAGE.equals(packageName) && !DidiPolicy.PACKAGE.equals(packageName)) {
                return;
            }

            ApplicationInfo ai = param.getApplicationInfo();
            if (ai == null || ai.sourceDir == null || !ai.sourceDir.startsWith("/data/app/")) {
                log(Log.WARN, TAG, "target path gate failed; original behavior preserved");
                return;
            }

            clientPackage = packageName;
            if (TARGET_PACKAGE.equals(packageName)) {
                installRouterHook(param.getDefaultClassLoader(), ai);
                if (routerInstalled && elemeMainProcess) nativePush.install(this);
            } else {
                installDidiLifecycleHook(param.getDefaultClassLoader());
            }
            installNotificationBuilderHook();
            installNotificationManagerHook();
            installNotificationBinderHook();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "package callback failed; original behavior preserved", t);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        try {
            String packageName = param.getPackageName();
            if (XmsfCompat.PACKAGE.equals(packageName)) {
                return;
            }
            if (SYSTEMUI_PACKAGE.equals(packageName)) {
                installSystemUiListenerHook(param.getClassLoader());
                // PackageReady supplies the final class loader after SystemUI
                // initialization; this is needed on builds that defer the
                // notification implementation until the ready callback.
                installFocusPermissionHook(param.getClassLoader(),
                        param.getApplicationInfo());
                installPluginFocusPermissionHook(param.getClassLoader(),
                        param.getApplicationInfo());
                installFocusPluginPostedHook(param.getClassLoader(),
                        param.getApplicationInfo());
                installFocusPayloadHook(param.getClassLoader(),
                        param.getApplicationInfo());
            } else if (DidiPolicy.PACKAGE.equals(packageName)) {
                installDidiLifecycleHook(param.getClassLoader());
            } else if (SYSTEMUI_PLUGIN_PACKAGE.equals(packageName)) {
                installPluginFocusPermissionHook(param.getClassLoader(),
                        param.getApplicationInfo());
                installFocusPluginPostedHook(param.getClassLoader(),
                        param.getApplicationInfo());
                installFocusPayloadHook(param.getClassLoader(),
                        param.getApplicationInfo());
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "package ready callback failed; original behavior preserved", t);
        }
    }

    /** Main SystemUI entry covers notifications posted on behalf of Didi by XMSF. */
    private synchronized void installSystemUiListenerHook(ClassLoader loader) {
        if (systemUiListenerInstalled) return;
        try {
            Class<?> listener;
            try {
                listener = Class.forName("com.android.systemui.statusbar.notification.MiuiNotificationListener", false, loader);
            } catch (ClassNotFoundException e) {
                listener = Class.forName("com.android.systemui.statusbar.NotificationListener", false, loader);
            }
            Method posted = listener.getDeclaredMethod("onNotificationPosted",
                    StatusBarNotification.class, NotificationListenerService.RankingMap.class);
            if (posted.getReturnType() != void.class) return;
            hook(posted).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object arg = chain.getArg(0);
                if (arg instanceof StatusBarNotification) {
                    StatusBarNotification sbn = (StatusBarNotification) arg;
                    if (DidiPolicy.PACKAGE.equals(sbn.getPackageName())) {
                        didi.decorate(sbn.getNotification(), DidiPolicy.notificationKey(sbn.getId(), sbn.getTag()));
                    }
                }
                return chain.proceed();
            });
            systemUiListenerInstalled = true;
            log(Log.INFO, TAG, "Didi SystemUI notification listener hook installed");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            log(Log.WARN, TAG, "Didi SystemUI listener unavailable; retaining plugin coverage");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Didi SystemUI listener installation failed", t);
        }
    }

    /** Each obfuscated layout is checked against its exact APK version at runtime. */
    private synchronized void installDidiLifecycleHook(ClassLoader loader) {
        if (didiLifecycleInstalled) return;
        for (DidiProfile profile : DidiProfile.VERIFIED) {
            if (installDidiLifecycleProfile(loader, profile)) {
                didiLifecycleInstalled = true;
                return;
            }
        }
        log(Log.WARN, TAG, "Didi verified live layouts unavailable; notification adaptation retained");
    }

    private boolean installDidiLifecycleProfile(ClassLoader loader, DidiProfile profile) {
        try {
            Class<?> window = Class.forName(profile.windowClass, false, loader);
            Class<?> base = Class.forName(profile.baseClass, false, loader);
            Class<?> data = Class.forName("com.didi.live.window.net.b", false, loader);
            Class<?> common = Class.forName("com.didi.live.window.common.b", false, loader);
            if (window.getSuperclass() != base || base.getSuperclass() != common) return false;
            if (data.getDeclaredField("a").getType() != int.class
                    || data.getDeclaredField("d").getType() != String.class
                    || !java.util.Map.class.isAssignableFrom(data.getDeclaredField("c").getType())) return false;
            Method create = window.getDeclaredMethod("c", int.class);
            Method current = null;
            int concreteGetters = 0;
            // DEX permits return-type-only bridge overloads; find the concrete one.
            for (Method candidate : window.getDeclaredMethods()) {
                if (candidate.getName().equals("d") && candidate.getParameterCount() == 0
                        && candidate.getReturnType() == data) {
                    current = candidate;
                    concreteGetters++;
                }
            }
            Method clear = base.getDeclaredMethod("a");
            if (create.getReturnType() != boolean.class || Modifier.isStatic(create.getModifiers())
                    || concreteGetters != 1 || current == null
                    || clear.getReturnType() != void.class || Modifier.isStatic(clear.getModifiers())) return false;
            Class<?> utils = Class.forName("com.didi.sdk.util.KotlinUtils", false, loader);
            final java.lang.reflect.Field contextField;
            final Method contextMethod;
            if (profile.contextGetter == null) {
                contextField = utils.getDeclaredField("a");
                contextMethod = null;
                if (!Modifier.isStatic(contextField.getModifiers())
                        || !Context.class.isAssignableFrom(contextField.getType())) return false;
                contextField.setAccessible(true);
            } else {
                contextField = null;
                contextMethod = utils.getDeclaredMethod(profile.contextGetter);
                if (!Modifier.isStatic(contextMethod.getModifiers())
                        || !Context.class.isAssignableFrom(contextMethod.getReturnType())) return false;
                contextMethod.setAccessible(true);
            }
            current.setAccessible(true);
            final Method getCurrent = current;
            deoptimize(create);
            deoptimize(clear);
            hook(create).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                didiDiagnostics.note(this, "create callback entered");
                Object result = chain.proceed();
                didiDiagnostics.note(this, "create result=" + Boolean.TRUE.equals(result));
                try {
                    Object context = contextMethod != null ? contextMethod.invoke(null) : contextField.get(null);
                    if (Boolean.TRUE.equals(result) && context instanceof Context) {
                        Context ctx = (Context) context;
                        if (DidiAdapter.isVerifiedVersion(ctx, profile)) {
                            didi.onLiveCreate(ctx, getCurrent.invoke(chain.getThisObject()),
                                    (Integer) chain.getArg(0));
                        }
                    }
                } catch (Throwable t) {
                    log(Log.WARN, TAG, "Didi live callback skipped; original result retained");
                }
                return result;
            });
            hook(clear).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object context = contextMethod != null ? contextMethod.invoke(null) : contextField.get(null);
                    if (window.isInstance(chain.getThisObject()) && context instanceof Context
                            && DidiAdapter.isVerifiedVersion((Context) context, profile)) {
                        didi.clearOwnedNotifications();
                    }
                } catch (Throwable t) {
                    log(Log.WARN, TAG, "Didi cleanup skipped; original result retained");
                }
                return result;
            });
            didiDiagnostics.install(this, loader, window, data, profile);
            log(Log.INFO, TAG, "Didi live callback hooks installed; version=" + profile.versionName
                    + "; class=" + profile.windowClass);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            return false;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Didi live hook installation failed; original behavior retained", t);
            return false;
        }
    }

    private void installRouterHook(ClassLoader classLoader, ApplicationInfo ai) {
        synchronized (this) {
            if (routerInstalled) {
                return;
            }

            try {
                Class<?> router = Class.forName(ROUTER_CLASS, false, classLoader);
                Method target = null;
                int candidates = 0;

                // In the verified 12.5.88 DEX this method's original name is
                // "a" (JADX labels it m514a): public boolean a(), no arguments.
                // Filtering every characteristic avoids selecting the SDK's
                // other DEX-legal return-type-only overloads named "a".
                for (Method method : router.getDeclaredMethods()) {
                    if ("a".equals(method.getName())
                            && method.getParameterTypes().length == 0
                            && boolean.class.equals(method.getReturnType())
                            && Modifier.isPublic(method.getModifiers())
                            && !Modifier.isStatic(method.getModifiers())) {
                        target = method;
                        candidates++;
                    }
                }

                if (candidates != 1 || target == null) {
                    log(Log.WARN, TAG, "MiPush signature gate failed; candidates=" + candidates
                            + "; original behavior preserved");
                    return;
                }

                target.setAccessible(true);
                hook(target).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> Boolean.FALSE);
                routerInstalled = true;
                log(Log.INFO, TAG, "Ele.me local CN MiPush route enabled"
                        + ", sourceDir=" + ai.sourceDir);
            } catch (ClassNotFoundException e) {
                log(Log.WARN, TAG, "verified MiPush router class absent; no-op", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook install failed; original behavior preserved", t);
            }
        }
    }

    /**
     * Inject before the notification leaves Ele.me.  This path is independent
     * of which SystemUI/plugin process eventually consumes the notification.
     */
    private void installNotificationManagerHook() {
        synchronized (this) {
            if (notificationManagerHookInstalled) {
                return;
            }

            try {
                int candidates = 0;
                for (Method method : NotificationManager.class.getDeclaredMethods()) {
                    boolean hasNotification = false;
                    for (Class<?> parameterType : method.getParameterTypes()) {
                        if (Notification.class.equals(parameterType)) {
                            hasNotification = true;
                            break;
                        }
                    }
                    if (!method.getName().startsWith("notify")
                            || !hasNotification
                            || !void.class.equals(method.getReturnType())) {
                        continue;
                    }

                    method.setAccessible(true);
                    hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                if (DidiPolicy.PACKAGE.equals(clientPackage)) {
                                    if ("notifyAsPackage".equals(method.getName())
                                            && !DidiPolicy.PACKAGE.equals(chain.getArg(0))) return chain.proceed();
                                    for (int i = 0; i < method.getParameterCount(); i++) {
                                        if (method.getParameterTypes()[i] == Notification.class && i > 0
                                                && chain.getArg(i - 1) instanceof Integer) {
                                            String tag = i > 1 && chain.getArg(i - 2) instanceof String
                                                    ? (String) chain.getArg(i - 2) : null;
                                            didi.decorate((Notification) chain.getArg(i),
                                                    DidiPolicy.notificationKey((Integer) chain.getArg(i - 1), tag));
                                        }
                                    }
                                    return chain.proceed();
                                }
                                Notification notification = null;
                                int id = 0;
                                String tag = null;
                                for (int i = 0; i < method.getParameterTypes().length; i++) {
                                    Object value = chain.getArg(i);
                                    if (value instanceof Notification) {
                                        notification = (Notification) value;
                                    } else if (value instanceof Integer) {
                                        id = (Integer) value;
                                    } else if (value instanceof String) {
                                        tag = (String) value;
                                    }
                                }
                                injectElemeFocusPayload(notification,
                                        TARGET_PACKAGE + ":" + id + ":" + (tag == null ? "" : tag));
                                return chain.proceed();
                            });
                    candidates++;
                }

                if (candidates == 0) {
                    log(Log.WARN, TAG,
                            "NotificationManager signature gate failed; original behavior preserved");
                    return;
                }
                notificationManagerHookInstalled = true;
                log(Log.INFO, TAG, "Ele.me NotificationManager hooks installed, candidates="
                        + candidates);
            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "NotificationManager hook install failed; original behavior preserved", t);
            }
        }
    }

    /**
     * First coverage point: decorate a just-built notification before the
     * application chooses a NotificationManager posting API.  This catches
     * SDK wrappers that do not call one of the public notify* methods.
     */
    private void installNotificationBuilderHook() {
        synchronized (this) {
            if (notificationBuilderHookInstalled) {
                return;
            }

            try {
                Method build = Notification.Builder.class.getDeclaredMethod("build");
                if (!Notification.class.equals(build.getReturnType())
                        || build.getParameterTypes().length != 0) {
                    log(Log.WARN, TAG,
                            "Notification.Builder signature gate failed; original behavior preserved");
                    return;
                }

                build.setAccessible(true);
                hook(build).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (result instanceof Notification) {
                                if (DidiPolicy.PACKAGE.equals(clientPackage)) {
                                    // Wait for notify/Binder to supply the real ID/tag.
                                } else {
                                    injectElemeFocusPayload((Notification) result,
                                            TARGET_PACKAGE + ":builder:" + System.identityHashCode(result));
                                }
                            }
                            return result;
                        });
                notificationBuilderHookInstalled = true;
                log(Log.INFO, TAG, "Ele.me Notification.Builder hook installed");
            } catch (NoSuchMethodException e) {
                log(Log.WARN, TAG,
                        "Notification.Builder API absent; manager/binder coverage retained", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "Notification.Builder hook install failed; original behavior preserved", t);
            }
        }
    }

    /**
     * Final coverage point: this is the framework Binder proxy used to hand a
     * notification to NotificationManagerService.  It closes the gap for
     * vendor or push SDK code that bypasses public NotificationManager APIs.
     */
    private void installNotificationBinderHook() {
        synchronized (this) {
            if (notificationBinderHookInstalled) {
                return;
            }

            try {
                Class<?> proxy = Class.forName("android.app.INotificationManager$Stub$Proxy",
                        false, NotificationManager.class.getClassLoader());
                int candidates = 0;
                for (Method method : proxy.getDeclaredMethods()) {
                    boolean hasNotification = false;
                    for (Class<?> parameterType : method.getParameterTypes()) {
                        if (Notification.class.equals(parameterType)) {
                            hasNotification = true;
                            break;
                        }
                    }
                    if (!method.getName().startsWith("enqueueNotification")
                            || !hasNotification
                            || !void.class.equals(method.getReturnType())) {
                        continue;
                    }

                    method.setAccessible(true);
                    hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                if (DidiPolicy.PACKAGE.equals(clientPackage)) {
                                    if (DidiPolicy.PACKAGE.equals(chain.getArg(0))) {
                                        for (int i = 1; i < method.getParameterCount(); i++) {
                                            if (method.getParameterTypes()[i] == Notification.class && i > 1
                                                    && chain.getArg(i - 1) instanceof Integer) {
                                                String tag = chain.getArg(i - 2) instanceof String
                                                        ? (String) chain.getArg(i - 2) : null;
                                                didi.decorate((Notification) chain.getArg(i),
                                                        DidiPolicy.notificationKey((Integer) chain.getArg(i - 1), tag));
                                            }
                                        }
                                    }
                                    return chain.proceed();
                                }
                                Notification notification = null;
                                String tag = null;
                                int id = 0;
                                boolean targetPackage = false;
                                for (int i = 0; i < method.getParameterTypes().length; i++) {
                                    Object value = chain.getArg(i);
                                    if (value instanceof Notification) {
                                        notification = (Notification) value;
                                    } else if (value instanceof Integer) {
                                        id = (Integer) value;
                                    } else if (value instanceof String) {
                                        String stringValue = (String) value;
                                        if (TARGET_PACKAGE.equals(stringValue)) {
                                            targetPackage = true;
                                        } else if (tag == null) {
                                            tag = stringValue;
                                        }
                                    }
                                }
                                if (targetPackage) {
                                    injectElemeFocusPayload(notification,
                                            TARGET_PACKAGE + ":" + id + ":"
                                                    + (tag == null ? "" : tag));
                                }
                                return chain.proceed();
                            });
                    candidates++;
                }

                if (candidates == 0) {
                    log(Log.WARN, TAG,
                            "Notification Binder signature gate failed; manager coverage retained");
                    return;
                }
                notificationBinderHookInstalled = true;
                log(Log.INFO, TAG,
                        "Ele.me Notification Binder hooks installed, candidates=" + candidates);
            } catch (ClassNotFoundException e) {
                log(Log.WARN, TAG,
                        "Notification Binder proxy absent; manager coverage retained", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "Notification Binder hook install failed; manager coverage retained", t);
            }
        }
    }

    /**
     * HyperOS stores a per-package focus switch in SystemUI. On this Xiaomi.eu
     * build the switch can remain false even when the package is present in the
     * ROM allow-list, causing both the client and Mi Fitness to downgrade the
     * notification before an island payload is requested or parsed.
     *
     * <p>Hook only the exact static API used by NotificationProvider, and only
     * return true for the two explicitly supported live-service packages. All
     * other packages and methods preserve the original SystemUI result.</p>
     */
    private void installFocusPermissionHook(ClassLoader classLoader, ApplicationInfo ai) {
        synchronized (this) {
            if (focusPermissionInstalled) {
                return;
            }

            try {
                if (ai == null || !SYSTEMUI_PACKAGE.equals(ai.packageName)
                        || ai.sourceDir == null
                        || !(ai.sourceDir.startsWith("/system/")
                        || ai.sourceDir.startsWith("/system_ext/")
                        || ai.sourceDir.startsWith("/product/"))) {
                    log(Log.WARN, TAG, "SystemUI path gate failed; no focus hook installed");
                    return;
                }

                Class<?> settings = Class.forName(
                        NOTIFICATION_SETTINGS_CLASS, false, classLoader);
                Method method = settings.getDeclaredMethod(
                        "canShowFocus", Context.class, String.class);
                int modifiers = method.getModifiers();
                if (!Modifier.isStatic(modifiers)
                        || !boolean.class.equals(method.getReturnType())) {
                    log(Log.WARN, TAG,
                            "SystemUI focus signature gate failed; original behavior preserved");
                    return;
                }

                method.setAccessible(true);
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object packageArg = chain.getArg(1);
                            if (packageArg instanceof String
                                    && FOCUS_PERMISSION_PACKAGES.contains(packageArg)) {
                                log(Log.INFO, TAG,
                                        "focus permission enabled for " + packageArg);
                                return Boolean.TRUE;
                            }
                            return chain.proceed();
                        });
                focusPermissionInstalled = true;
                log(Log.INFO, TAG, "SystemUI focus permission hook installed"
                        + ", sourceDir=" + ai.sourceDir);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log(Log.WARN, TAG,
                        "verified SystemUI focus API absent; original behavior preserved", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "SystemUI focus hook install failed; original behavior preserved", t);
            }
        }
    }

    /**
     * The shipped MiuiSystemUIPlugin contains a second, instance-based
     * NotificationSettingsManager. FocusNotifUtils reaches this variant
     * while the public content provider reaches the main SystemUI variant
     * above, so keep both gates aligned.
     */
    private void installPluginFocusPermissionHook(ClassLoader classLoader, ApplicationInfo ai) {
        synchronized (this) {
            if (pluginFocusPermissionInstalled) {
                return;
            }

            try {
                if (ai == null || ai.sourceDir == null
                        || !(ai.sourceDir.startsWith("/product/")
                        || ai.sourceDir.startsWith("/system_ext/")
                        || ai.sourceDir.startsWith("/system/"))) {
                    log(Log.WARN, TAG,
                            "SystemUI plugin path gate failed; no plugin focus hook installed");
                    return;
                }

                Class<?> settings = Class.forName(
                        PLUGIN_NOTIFICATION_SETTINGS_CLASS, false, classLoader);
                Method method = settings.getDeclaredMethod(
                        "canShowFocus", Context.class, String.class);
                int modifiers = method.getModifiers();
                if (Modifier.isStatic(modifiers)
                        || !boolean.class.equals(method.getReturnType())) {
                    log(Log.WARN, TAG,
                            "SystemUI plugin focus signature gate failed; original behavior preserved");
                    return;
                }

                method.setAccessible(true);
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object packageArg = chain.getArg(1);
                            if (packageArg instanceof String
                                    && FOCUS_PERMISSION_PACKAGES.contains(packageArg)) {
                                log(Log.INFO, TAG,
                                        "plugin focus permission enabled for " + packageArg);
                                return Boolean.TRUE;
                            }
                            return chain.proceed();
                        });
                pluginFocusPermissionInstalled = true;
                log(Log.INFO, TAG, "SystemUI plugin focus permission hook installed"
                        + ", sourceDir=" + ai.sourceDir);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log(Log.WARN, TAG,
                        "verified SystemUI plugin focus API absent; original behavior preserved", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "SystemUI plugin focus hook install failed; original behavior preserved", t);
            }
        }
    }

    /**
     * Ele.me currently posts an ordinary BigText notification.  The focus
     * controller rejects it before the pre-handler unless the notification
     * contains miui.focus.param, so add a small OS3-compatible V3 template at
     * the controller boundary.  Genuine app-provided focus payloads are never
     * replaced.
     */
    private void installFocusPayloadHook(ClassLoader classLoader, ApplicationInfo ai) {
        synchronized (this) {
            if (focusPayloadInstalled) {
                return;
            }

            try {
                if (ai == null || ai.sourceDir == null
                        || !(ai.sourceDir.startsWith("/product/")
                        || ai.sourceDir.startsWith("/system_ext/")
                        || ai.sourceDir.startsWith("/system/"))) {
                    log(Log.WARN, TAG,
                            "SystemUI plugin path gate failed; no focus payload hook installed");
                    return;
                }

                Class<?> controller = Class.forName(
                        FOCUS_NOTIFICATION_CONTROLLER_CLASS, false, classLoader);
                Class<?> provider = Class.forName(
                        "com.android.systemui.plugins.NotificationListenerController$NotificationProvider",
                        false, classLoader);
                Method target = controller.getDeclaredMethod("onNotificationPosted",
                        StatusBarNotification.class, Context.class, Context.class, provider);

                target.setAccessible(true);
                hook(target).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object arg = chain.getArg(0);
                            if (arg instanceof StatusBarNotification) {
                                injectElemeFocusPayload((StatusBarNotification) arg);
                            }
                            return chain.proceed();
                        });
                focusPayloadInstalled = true;
                log(Log.INFO, TAG, "focus controller payload hook installed"
                        + ", sourceDir=" + ai.sourceDir);
            } catch (ClassNotFoundException e) {
                log(Log.WARN, TAG,
                        "verified focus controller class absent; retrying on package ready", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "focus payload hook install failed; original behavior preserved", t);
            }
        }
    }

    /**
     * Main SystemUI receives plugin callbacks through this method.  It has no
     * plugin-only provider parameter, so it remains reflectable even when a
     * secondary plugin process has only a partial class path.
     */
    private void installFocusPluginPostedHook(ClassLoader classLoader, ApplicationInfo ai) {
        synchronized (this) {
            if (focusPluginPostedInstalled) {
                return;
            }

            try {
                if (ai == null || ai.sourceDir == null
                        || !(ai.sourceDir.startsWith("/product/")
                        || ai.sourceDir.startsWith("/system_ext/")
                        || ai.sourceDir.startsWith("/system/"))) {
                    log(Log.WARN, TAG,
                            "SystemUI plugin path gate failed; no plugin callback hook installed");
                    return;
                }

                Class<?> plugin = Class.forName(FOCUS_PLUGIN_IMPL_CLASS, false, classLoader);
                Method target = plugin.getDeclaredMethod("onNotificationPosted",
                        StatusBarNotification.class, NotificationListenerService.RankingMap.class);
                if (!boolean.class.equals(target.getReturnType())) {
                    log(Log.WARN, TAG,
                            "focus plugin callback signature gate failed; original behavior preserved");
                    return;
                }

                target.setAccessible(true);
                hook(target).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object arg = chain.getArg(0);
                            if (arg instanceof StatusBarNotification) {
                                injectElemeFocusPayload((StatusBarNotification) arg);
                            }
                            return chain.proceed();
                        });
                focusPluginPostedInstalled = true;
                log(Log.INFO, TAG, "focus plugin callback hook installed"
                        + ", sourceDir=" + ai.sourceDir);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log(Log.WARN, TAG,
                        "focus plugin callback API absent; retrying on package ready", e);
            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "focus plugin callback hook install failed; original behavior preserved", t);
            }
        }
    }

    private void injectElemeFocusPayload(StatusBarNotification sbn) {
        try {
            if (sbn != null && DidiPolicy.PACKAGE.equals(sbn.getPackageName())) {
                didi.decorate(sbn.getNotification(), DidiPolicy.notificationKey(sbn.getId(), sbn.getTag()));
                return;
            }
            if (sbn == null || !TARGET_PACKAGE.equals(sbn.getPackageName())) {
                return;
            }

            Notification notification = sbn.getNotification();
            String key = sbn.getKey();
            if (key == null || key.isEmpty()) {
                key = TARGET_PACKAGE + ":" + sbn.getId();
            }
            injectElemeFocusPayload(notification, key);
        } catch (Throwable t) {
            log(Log.ERROR, TAG,
                    "Ele.me focus payload injection failed; original notification preserved", t);
        }
    }

    private void injectElemeFocusPayload(Notification notification, String key) {
        try {
            if (notification == null || notification.extras == null
                    || !isElemeOrderNotification(notification)) {
                return;
            }

            Bundle extras = notification.extras;
            String existing = extras.getString(FOCUS_PARAM);
            boolean generated = extras.getBoolean(AUTO_FOCUS_MARKER, false);
            if (existing != null && !existing.isEmpty() && !generated) {
                return;
            }

            String title = charSequenceToString(extras.getCharSequence(Notification.EXTRA_TITLE));
            String text = charSequenceToString(extras.getCharSequence(Notification.EXTRA_TEXT));
            if (title.isEmpty()) {
                title = "饿了么订单";
            }
            if (text.isEmpty()) {
                text = "订单状态已更新";
            }

            String safeKey = key == null || key.isEmpty()
                    ? TARGET_PACKAGE + ":notification" : key;
            String notifyId = safeKey;
            long sequence = System.currentTimeMillis();

            JSONObject v2 = new JSONObject();
            v2.put("protocol", 1);
            v2.put("business", "foodDelivery");
            v2.put("scene", "foodDelivery");
            v2.put("enableFloat", true);
            v2.put("updatable", true);
            v2.put("islandFirstFloat", true);
            v2.put("reopen", "reopen");
            v2.put("aodTitle", title);
            v2.put("aodPic", "");
            v2.put("ticker", title);
            v2.put("tickerPic", "");
            v2.put("tickerPicDark", "");
            v2.put("timeout", 120);
            v2.put("showSmallIcon", true);
            v2.put("isShowNotification", true);
            v2.put("notifyId", notifyId);
            v2.put("orderId", safeKey);
            v2.put("outEffectSrc", "");
            v2.put("outEffectColor", "");
            v2.put("cancel", false);
            v2.put("hideDeco", false);
            v2.put("sequence", sequence);
            v2.put("filterWhenNoPermission", false);

            JSONObject baseInfo = new JSONObject();
            baseInfo.put("title", title);
            baseInfo.put("content", text);
            baseInfo.put("subTitle", "");
            baseInfo.put("colorTitle", "#3482FF");
            baseInfo.put("colorTitleDark", "#7EB6FF");
            baseInfo.put("type", 2);
            baseInfo.put("showDivider", false);
            baseInfo.put("showContentDivider", false);
            v2.put("baseInfo", baseInfo);

            JSONObject root = new JSONObject();
            root.put("param_v2", v2);
            extras.putString(FOCUS_PARAM, root.toString());
            extras.putBoolean(AUTO_FOCUS_MARKER, true);
            log(Log.INFO, TAG, "Ele.me focus payload injected key=" + safeKey
                    + " title=" + title);
        } catch (Throwable t) {
            log(Log.ERROR, TAG,
                    "Ele.me focus payload injection failed; original notification preserved", t);
        }
    }

    private static boolean isElemeOrderNotification(Notification notification) {
        String channel = notification.getChannelId();
        CharSequence title = notification.extras != null
                ? notification.extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence text = notification.extras != null
                ? notification.extras.getCharSequence(Notification.EXTRA_TEXT) : null;
        String combined = charSequenceToString(title) + " " + charSequenceToString(text);
        if (combined.isEmpty()) {
            return false;
        }
        return combined.contains("订单") || combined.contains("骑士")
                || combined.contains("商家") || combined.contains("配送")
                || combined.contains("送达") || combined.contains("已接单")
                || combined.contains("取餐") || combined.contains("外卖")
                || "eleme".equals(channel);
    }

    private static String charSequenceToString(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
