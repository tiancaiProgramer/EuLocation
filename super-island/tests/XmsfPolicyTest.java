package com.ux5.hyperos3.elemeisland;
public final class XmsfPolicyTest {
    private static int checks;
    private static void check(boolean b, String message) { checks++; if (!b) throw new AssertionError(message); }
    public static void main(String[] args) {
        String pkg="com.xiaomi.xmsf", version="6.2.67-G";long code=60002067L;
        check(XmsfPolicy.allow(false,pkg,version,code,true,"China"),"verified blocked gate restored");
        check(XmsfPolicy.allow(true,pkg,version,code,true,"China"),"original true retained");
        check(!XmsfPolicy.allow(false,pkg,"7.5.14-C",code,true,"China"),"CN APK untouched");
        check(!XmsfPolicy.allow(false,pkg,version,60002068L,true,"China"),"other version code untouched");
        check(!XmsfPolicy.allow(false,"me.ele",version,code,true,"China"),"other package untouched");
        check(!XmsfPolicy.allow(false,pkg,version,code,false,"China"),"non-MIUI disable respected");
        for (String region:new String[]{"Global","Europe","CN","",null}) check(!XmsfPolicy.allow(false,pkg,version,code,true,region),"other region original false retained");
        check(!XmsfPolicy.allow(false,pkg,null,code,true,"China"),"missing version unchanged");
        check(XmsfPolicy.allow(true,"other",null,0,false,null),"unrelated original true retained");
        check("app.chat.xiaomi.net".equals(XmsfPolicy.chatHost("app.chat.global.xiaomi.net",true)),"China host");
        check("unchanged".equals(XmsfPolicy.chatHost("unchanged",false)),"other host retained");
        check("https://register.xmpush.xiaomi.com/pass/v2/register/encrypt".equals(XmsfPolicy.registrationUrl("https:///bad",true,true)),"China encrypted registration");
        check("https://register.xmpush.xiaomi.com/pass/v2/register".equals(XmsfPolicy.registrationUrl("https:///bad",false,true)),"China plain native registration URL");
        check("unchanged".equals(XmsfPolicy.registrationUrl("unchanged",true,false)),"unverified endpoint retained");
        System.out.println("XmsfPolicyTest PASS " + checks);
    }
}
