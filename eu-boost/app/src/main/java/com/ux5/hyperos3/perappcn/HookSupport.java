package com.ux5.hyperos3.perappcn;

import android.content.pm.ApplicationInfo;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Locale;

final class HookSupport {
    static boolean verified(ApplicationInfo info, String path, String hash) throws Exception {
        if (android.os.Build.VERSION.SDK_INT != 36 || info == null
                || (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || !path.equals(info.sourceDir)) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        return hash.equals(hex.toString());
    }
    static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static boolean writeBoolean(Field field, boolean value) {
        try {
            field.setBoolean(null, value);
            if (field.getBoolean(null) == value) return true;
        } catch (Throwable ignored) { }
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field singleton = type.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            Object unsafe = singleton.get(null);
            Object base = type.getMethod("staticFieldBase", Field.class).invoke(unsafe, field);
            long offset = ((Long) type.getMethod("staticFieldOffset", Field.class).invoke(unsafe, field)).longValue();
            type.getMethod("putBoolean", Object.class, long.class, boolean.class).invoke(unsafe, base, offset, value);
            return field.getBoolean(null) == value;
        } catch (Throwable ignored) { return false; }
    }
    private HookSupport() { }
}
