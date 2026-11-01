package com.ux5.hyperos3.elemeisland;

/** Exact APK versions inspected locally. Obfuscated names must never be shared across versions. */
final class DidiProfile {
    static final DidiProfile V8010 = new DidiProfile("8.0.10", 1208001006L, "p90.a", "t90.a", null);
    static final DidiProfile V8014 = new DidiProfile("8.0.14", 1208001404L, "b90.a", "f90.a", "n");
    static final DidiProfile[] VERIFIED = {V8010, V8014};
    final String versionName;
    final long versionCode;
    final String windowClass;
    final String baseClass;
    final String contextGetter;

    private DidiProfile(String name, long code, String window, String base, String context) {
        versionName = name;
        versionCode = code;
        windowClass = window;
        baseClass = base;
        contextGetter = context;
    }

    static DidiProfile forVersion(String name, long code) {
        for (DidiProfile profile : VERIFIED) {
            if (profile.versionName.equals(name) && profile.versionCode == code) return profile;
        }
        return null;
    }
}
