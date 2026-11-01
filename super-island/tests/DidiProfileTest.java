package com.ux5.hyperos3.elemeisland;

public final class DidiProfileTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(DidiProfile.forVersion("8.0.10", 1208001006L) == DidiProfile.V8010, "old profile preserved");
        check(DidiProfile.forVersion("8.0.14", 1208001404L) == DidiProfile.V8014, "installed build supported");
        check(DidiProfile.V8014.windowClass.equals("b90.a"), "new Xiaomi class");
        check(DidiProfile.V8014.baseClass.equals("f90.a"), "new clear callback owner");
        check(DidiProfile.V8014.contextGetter.equals("n"), "new context getter");
        check(DidiProfile.V8010.contextGetter == null, "old context field retained");
        check(DidiProfile.forVersion("8.0.14", 1208001405L) == null, "uninspected build rejected");
        check(DidiProfile.forVersion("8.0.10", 1208001404L) == null, "mixed version rejected");
        check(DidiProfile.forVersion(null, 1208001404L) == null, "null version rejected");
        check(DidiProfile.forVersion("8.0.15", 1208001500L) == null, "future version rejected");
        System.out.println("PASS " + checks + " Didi version profile cases");
    }
}
