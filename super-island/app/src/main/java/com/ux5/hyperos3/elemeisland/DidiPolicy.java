package com.ux5.hyperos3.elemeisland;

import java.util.Locale;

/** Pure policy: excludes receipts, advertising, driver chats and unrelated services. */
final class DidiPolicy {
    static final String PACKAGE = "com.sdu.didi.psnger";
    static final String LIVE_CHANNEL = "Didi_Notify_Live";

    static boolean isRide(String channel, String title, String text) {
        String value = (safe(title) + " " + safe(text)).toLowerCase(Locale.ROOT);
        if (value.trim().isEmpty() || value.length() > 4096) return false;
        if (contains(value, "优惠券", "领券", "红包", "抽奖", "限时优惠", "打车优惠", "推广", "coupon")) return false;
        if (isTerminal(value)) return false;
        String channelName = safe(channel).toLowerCase(Locale.ROOT);
        if (contains(channelName, "chat", "keepalive", "voip", "foreground")) return false;
        // The inspected live channel is reserved for real-time activities.
        if (LIVE_CHANNEL.equals(channel)) return true;
        return contains(value, "司机已接单", "司机已到达", "司机即将到达", "司机正在赶来", "司机正在前往",
                "车辆已到达", "正在为您派单", "正在为你派单", "正在为您寻找司机", "请前往上车点",
                "司机正在接驾", "司机距您", "司机距离您", "等待上车", "正在接驾",
                "行程进行中", "已开始行程", "正在前往目的地", "距离上车点", "driver is arriving",
                "driver has arrived", "driver is on the way", "ride in progress");
    }

    static boolean isTerminal(String value) {
        value = safe(value).toLowerCase(Locale.ROOT);
        return contains(value, "行程已结束", "行程结束", "行程已完成", "行程取消", "订单已取消", "订单取消",
                "订单已完成", "取消订单", "取消成功", "支付成功", "付款成功", "待支付", "请支付", "待评价", "电子发票",
                "trip completed", "ride completed", "ride cancelled", "ride canceled", "payment successful");
    }

    static String notificationKey(int id, String tag) {
        String value = safe(tag);
        return PACKAGE + ":" + id + ":" + value.length() + ":" + value;
    }

    static String safe(String value) { return value == null ? "" : value.trim(); }

    private static boolean contains(String value, String... options) {
        for (String option : options) if (value.contains(option)) return true;
        return false;
    }
}
