package com.ux5.hyperos3.elemeisland;

/** Host-side regression cases; no mocked Android runtime or fake test orders. */
public final class DidiPolicyTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check(DidiPolicy.isRide("Didi_Notify_Live", "滴滴出行", "等待车辆到达"), "dedicated live channel");
        check(DidiPolicy.isRide("default", "滴滴出行", "司机已接单，正在前往上车点"), "driver accepted");
        check(DidiPolicy.isRide("default", "司机已到达", "请准备上车"), "driver arrived");
        check(DidiPolicy.isRide("default", "滴滴出行", "行程进行中"), "in progress");
        check(DidiPolicy.isRide("default", "Your ride", "Your driver is arriving"), "english arrival");
        check(DidiPolicy.isRide("default", "滴滴出行", "司机距您还有500米"), "distance update");
        check(!DidiPolicy.isRide(null, null, null), "empty input");
        check(!DidiPolicy.isRide("Didi_Notify_Live", "", ""), "empty live channel");
        check(!DidiPolicy.isRide("default", "滴滴出行", "您有一张优惠券"), "coupon");
        check(!DidiPolicy.isRide("Didi_Notify_Live", "领券享优惠", "司机已接单"), "marketing takes precedence");
        check(!DidiPolicy.isRide("Didi Chat", "司机已接单", "聊天内容"), "driver chat");
        check(!DidiPolicy.isRide("didi_chat", "司机已到达", "聊天内容"), "chat variant");
        check(!DidiPolicy.isRide("notification_channel_foreground_service", "司机已接单", "后台服务"), "service");
        check(!DidiPolicy.isRide("Didi_Notify_Live", "行程已结束", "请评价本次行程"), "completed");
        check(!DidiPolicy.isRide("Didi_Notify_Live", "订单已取消", "司机已接单"), "cancelled beats status");
        check(!DidiPolicy.isRide("default", "已成功取消订单", "司机已接单"), "cancel wording");
        check(!DidiPolicy.isRide("Didi_Notify_Live", "支付成功", "行程进行中"), "payment receipt");
        check(!DidiPolicy.isRide("default", "电子发票", "司机已接单"), "invoice");
        check(!DidiPolicy.isRide("default", "滴滴出行", "点击打开应用"), "unrelated ordinary notification");
        check(!DidiPolicy.isRide("Didi_Notify_Live", "x".repeat(4097), ""), "bounded text");
        check(DidiPolicy.notificationKey(10, "tag").equals(DidiPolicy.notificationKey(10, "tag")), "stable key");
        check(!DidiPolicy.notificationKey(10, "tag").equals(DidiPolicy.notificationKey(11, "tag")), "distinct ids");
        check(!DidiPolicy.notificationKey(10, "tag").equals(DidiPolicy.notificationKey(10, "other")), "distinct tags");
        check(DidiPolicy.notificationKey(10, null).equals(DidiPolicy.notificationKey(10, "")), "nullable tag");
        System.out.println("PASS " + checks + " Didi policy cases");
    }
}
