# 实时服务超级岛适配 v0.15 测试版

开发目录：`G:\Data\ApkFuture\SuperIsland`。源码 `app/src/main`，构建 `tools/build_standalone.py`，新APK在 `dist`，实机记录在 `testing`。

## 当前变更与结果

v0.15 为淘宝闪购前台启动补齐原生MiPush服务启动请求。现有SDK路由Hook选择应用自身服务；本轮失效时该组件已启用、注册有效，但活动服务中没有XMPushService。把应用切回前台并启动该服务后，真实订单与可见超级岛恢复。

新版在已验证路由Hook成功、且当前为me.ele主进程时，安装Instrumentation.callActivityOnResume处理。原回调先执行，再检查服务包名、声明进程及组件启用状态，调用应用自身startService。每个主进程成功请求一次即停止尝试；失败只允许在后续resume且间隔至少30秒时再试。无定时任务、后台保活、模拟订单、账号/注册/区域改写。

XMSF仍沿用v0.14：与EU增强0.9.1一样直接返回，不安装getter或方法拦截。不能把这个no-op作用域本身宣称为CN改写或恢复的已证实原因。

2026-11-01实机：已覆盖安装code15，只重启淘宝闪购。12:19:53模块自动启动原生服务，12:19:55真实订单通知注入焦点载荷，用户确认返回桌面可见超级岛。未手动启动服务来冒充新版自动行为。原有淘宝闪购通知和SystemUI处理、滴滴策略保持一致。

**验证边界：** 应用进程重启后的前台自动启动→真实订单通知→可见岛已通过；整机冷启动、无需打开应用的后台恢复、其他订单和长期稳定性尚未验证。

同笔订单的“已送达”显示也已确认：12:26:20新的原生通知载荷到达后，超级岛同步显示已送达。用户此前进入过详情页，因此这次不能证明无需打开应用的后台及时更新，也不能仅凭时间相邻断言后台通道失效。观察见 `testing/Eleme-Live-20261101/送达更新观察.txt`。

## 保留的实测配置

超级岛作用域：淘宝闪购/饿了么`me.ele`、滴滴`com.sdu.didi.psnger`、系统界面`com.android.systemui`、系统界面组件`miui.systemui.plugin`、小米服务框架`com.xiaomi.xmsf`。

EU增强取消XMSF；其他原有模块勾选保持。该组合已有本轮成功证据，不据此断言每个勾选都是必要条件。小爱/钱包/卡包仍由EuLocation负责。

## 检查与记录

- `tools/test_native_push_start.py`：生产处理器的35项门槛、重试、异常、原回调语义检查。
- `tools/test_xmsf_scope.py`：先构建，再运行真实Main的12项无拦截回调检查。
- `tools/test_didi.ps1`：24项策略、19项载荷、10项版本检查；v0.15对应源码和通知处理实现与已通过的版本相同。
- APK编译、签名、zipalign检查通过，延续原签名。

原始对照与结论修订：`testing/Eleme-Live-20261101`。最新：`v015-build/实测结果.txt`。v0.13未安装，不作为修复版；v0.14单独复制XMSF no-op曾未恢复，不能把它标成完整修复。

临时采集已停止。本版已发布：[GitHub Release](https://github.com/tiancaiProgramer/EuLocation/releases/tag/v0.5.0-eu-suite-test)。手环测试保持暂停，手机上岛不代表手环验证通过。
