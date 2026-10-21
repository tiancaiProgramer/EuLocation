package com.ux5.hyperos3.perappcn;

public final class PlatformProfileTest {
    public static void main(String[] args) {
        int cases = 0;
        for (String device : new String[]{"dada", "babylon", "other_device"}) {
            for (String build : new String[]{"OS3.0.307.0.WOCCNXM", "OS3.0.1.0.OTHER"}) {
                for (String suffix : new String[]{"_xiaomieu_global", "_xiaomieu"}) {
                    check(PlatformProfile.matches(36, "arm64-v8a", build, device + suffix));
                    cases++;
                }
            }
        }
        check(!PlatformProfile.matches(35, "arm64-v8a", "OS3.0", "dada_xiaomieu"));
        check(!PlatformProfile.matches(36, "x86_64", "OS3.0", "dada_xiaomieu"));
        check(!PlatformProfile.matches(36, "arm64-v8a", "OS2.0", "dada_xiaomieu"));
        check(!PlatformProfile.matches(36, "arm64-v8a", "OS3.0", "dada_global"));
        check(!PlatformProfile.matches(36, null, null, null));
        System.out.println("PASS: " + (cases + 5) + " Java profile cases (simulated, not device tests)");
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Profile guard regression");
    }
}
