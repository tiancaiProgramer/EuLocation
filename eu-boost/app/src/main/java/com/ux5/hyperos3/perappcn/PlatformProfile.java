package com.ux5.hyperos3.perappcn;

/** No codename/full-build lock; safety boundaries for the Settings adaptation only. */
final class PlatformProfile {
    static boolean matches(int sdk, String abi, String incremental, String modDevice) {
        return sdk == 36 && "arm64-v8a".equals(abi)
                && incremental != null && incremental.startsWith("OS3.")
                && modDevice != null && (modDevice.endsWith("_xiaomieu_global")
                    || modDevice.endsWith("_xiaomieu"));
    }
}
