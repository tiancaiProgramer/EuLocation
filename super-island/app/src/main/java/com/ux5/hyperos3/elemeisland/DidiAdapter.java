package com.ux5.hyperos3.elemeisland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** App-side fallback and SystemUI decoration use exactly the same taxi payload. */
final class DidiAdapter {
    private static final String PARAM = "miui.focus.param";
    private static final String MARKER = "com.ux5.hyperos3.elemeisland.didi";
    private static final String ORDER = "com.ux5.hyperos3.elemeisland.didi.order";
    private static final String CONTENT = "com.ux5.hyperos3.elemeisland.didi.content";
    private static final String GENERATED_PARAM = "com.ux5.hyperos3.elemeisland.didi.param";
    private static final String OWN_TAG = "ux5.didi.live";
    private final Consumer<String> logger;
    private final Map<String, Integer> owned = new LinkedHashMap<>();
    private NotificationManager manager;
    private long generation;

    DidiAdapter(Consumer<String> logger) { this.logger = logger; }

    static boolean isVerifiedVersion(Context context, DidiProfile profile) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(DidiPolicy.PACKAGE, 0);
            return DidiProfile.forVersion(info.versionName, info.getLongVersionCode()) == profile;
        } catch (Exception e) { return false; }
    }

    void decorate(Notification notification, String key) {
        try {
            if (notification == null || notification.extras == null) return;
            Bundle extras = notification.extras;
            String nativeParam = extras.getString(PARAM);
            // Native payloads (including cancellation and sequence) always win.
            if (nativeParam != null && !nativeParam.isEmpty()
                    && (!extras.getBoolean(MARKER, false)
                    || !nativeParam.equals(extras.getString(GENERATED_PARAM)))) return;
            if (extras.containsKey("miui.focus.param.custom") || extras.containsKey("miui.focus.param.media")) return;
            if ((notification.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_FOREGROUND_SERVICE)) != 0) return;
            String title = text(extras, Notification.EXTRA_TITLE);
            String body = text(extras, Notification.EXTRA_BIG_TEXT);
            if (body.isEmpty()) body = text(extras, Notification.EXTRA_TEXT);
            if (!DidiPolicy.isRide(notification.getChannelId(), title, body)) {
                // A reused notification may change to a receipt/terminal status.
                if (extras.getBoolean(MARKER, false)) {
                    extras.remove(PARAM);
                    extras.remove(MARKER);
                    extras.remove(CONTENT);
                    extras.remove(GENERATED_PARAM);
                }
                return;
            }
            String actualTitle = title.isEmpty() ? body : title;
            String actualBody = body.isEmpty() ? title : body;
            String order = extras.getString(ORDER, key);
            long sequence = System.currentTimeMillis();
            // Manager, Binder and SystemUI see the same object: do not generate extra updates.
            String contentKey = key + "\n" + actualTitle + "\n" + actualBody;
            if (extras.getBoolean(MARKER, false) && nativeParam != null
                    && contentKey.equals(extras.getString(CONTENT))) return;
            Icon icon = notification.getSmallIcon();
            String encoded = DidiPayload.build(actualTitle, actualBody, key, order, sequence, icon != null).toString();
            if (icon != null) {
                Bundle existingPics = extras.getBundle("miui.focus.pics");
                Bundle pics = existingPics == null ? new Bundle() : new Bundle(existingPics);
                pics.putParcelable(DidiPayload.PIC, icon);
                extras.putBundle("miui.focus.pics", pics);
            }
            extras.putString(PARAM, encoded);
            extras.putString(GENERATED_PARAM, encoded);
            extras.putBoolean(MARKER, true);
            extras.putString(CONTENT, contentKey);
            // No order ID, notification text, account token or location in logs.
            logger.accept("Didi taxi notification decorated; channel=" + notification.getChannelId());
        } catch (Throwable e) {
            logger.accept("Didi notification decoration skipped: " + e.getClass().getSimpleName());
        }
    }

    /** Uses only real successful live-data callbacks, never replayed or guessed order state. */
    void onLiveCreate(Context context, Object data, int id) {
        try {
            if (data == null || !DidiPolicy.PACKAGE.equals(context.getPackageName())) return;
            if (!Integer.valueOf(0).equals(field(data, "a"))) return;
            String order = String.valueOf(field(data, "d"));
            Object raw = field(data, "c");
            if (order.isEmpty() || "null".equals(order) || !(raw instanceof Map)) return;
            Map<?, ?> map = (Map<?, ?>) raw;
            JSONObject json = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    json.put((String) entry.getKey(), entry.getValue());
                }
            }
            String[] display = DidiPayload.display(json);
            String title = display[0];
            String content = display[1];
            if (DidiPolicy.isTerminal(title + " " + content)) {
                clearOwnedNotifications();
                return;
            }
            if (!DidiPolicy.isRide(DidiPolicy.LIVE_CHANNEL, title, content)) {
                logger.accept("Didi live callback has no usable current display text; awaiting push");
                return;
            }
            Context app = context.getApplicationContext();
            final String displayTitle = title;
            final String displayContent = content;
            final long epoch;
            synchronized (this) { epoch = ++generation; }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                synchronized (DidiAdapter.this) {
                    if (epoch != generation) return;
                    postLiveFallback(app, id, order, displayTitle, displayContent);
                }
            }, 1500);
        } catch (Throwable e) {
            logger.accept("Didi live data unavailable: " + e.getClass().getSimpleName());
        }
    }

    private void postLiveFallback(Context context, int id, String order, String title, String text) {
        try {
            manager = context.getSystemService(NotificationManager.class);
            if (manager == null || !manager.areNotificationsEnabled()) return;
            for (StatusBarNotification active : manager.getActiveNotifications()) {
                Bundle extras = active.getNotification().extras;
                if (extras != null && extras.containsKey(PARAM) && !extras.getBoolean(MARKER, false)) {
                    clearOwnedNotifications();
                    logger.accept("Didi native island already active; fallback skipped");
                    return;
                }
            }
            manager.createNotificationChannel(new NotificationChannel(DidiPolicy.LIVE_CHANNEL,
                    "实时活动", NotificationManager.IMPORTANCE_DEFAULT));
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(DidiPolicy.PACKAGE);
            if (launch == null) return;
            PendingIntent click = PendingIntent.getActivity(context, id, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Bundle extras = new Bundle();
            extras.putString(ORDER, order);
            int icon = context.getApplicationInfo().icon;
            if (icon == 0) return;
            Notification notification = new Notification.Builder(context, DidiPolicy.LIVE_CHANNEL)
                    .setSmallIcon(icon).setContentTitle(title).setContentText(text)
                    .setContentIntent(click).setOnlyAlertOnce(true).setTimeoutAfter(180000)
                    .addExtras(extras).build();
            decorate(notification, DidiPolicy.notificationKey(id, OWN_TAG));
            if (!notification.extras.getBoolean(MARKER, false)) return;
            // Retire an older callback's owned notification, not app/native notifications.
            for (Integer old : owned.values()) if (old != id) manager.cancel(OWN_TAG, old);
            owned.clear();
            owned.put(order, id);
            manager.notify(OWN_TAG, id, notification);
            logger.accept("Didi real live data fallback posted");
        } catch (Throwable e) {
            logger.accept("Didi live fallback skipped: " + e.getClass().getSimpleName());
        }
    }

    synchronized void clearOwnedNotifications() {
        generation++;
        try {
            if (manager != null) for (Integer id : owned.values()) manager.cancel(OWN_TAG, id);
        } catch (Throwable e) {
            logger.accept("Didi fallback cleanup skipped: " + e.getClass().getSimpleName());
        }
        owned.clear();
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static String text(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }
}
