# EuLocation · HyperOS 3 EU 增强测试套件

实机基线：**小米15（dada）／xiaomi.eu HyperOS 3 OS3.0.307.0.WOCCNXM／Android 16 arm64**，KernelSU＋已启用的挂载元模块、兼容 libxposed API 102 的 LSPosed。其它机型和 EU 版本未实测。本项目不是定位模块。

[下载测试版](https://github.com/tiancaiProgramer/EuLocation/releases/tag/v0.6.0-slim-suite-test) · [全部 Releases](https://github.com/tiancaiProgramer/EuLocation/releases)

| 文件 | 安装方式 | 用途 |
| --- | --- | --- |
| EuLocation-v0.6.0-slim-test.zip | KernelSU 刷入 | 卡包四个后台、超级小爱核心＋唤醒、原生搜索，可分别选择 |
| MiWallet-6.111.0.5701.2736-original.apk | 可选普通 APK | 小米原签名钱包主 App；只用设置卡包无需安装 |
| Island-v0.18-xmsf-china-route-test.apk | LSPosed | 淘宝闪购／饿了么、滴滴及焦点通知适配，修复指定 EU 小米服务框架的国内推送登记 |
| EUBoost-v0.10.1-browser-fix-test.apk | LSPosed，沿用上一 Release | 应用录音、设置卡包入口、负一屏、局部系统应用更新许可、桌面搜索及 Google 浏览器跳转 |

本次更新前三项；EU增强仍为 v0.10.1，可从[上一测试版](https://github.com/tiancaiProgramer/EuLocation/releases/tag/v0.5.0-eu-suite-test)下载。附件相互独立，不是一个 APK。核对 SHA256SUMS.txt，**不要刷 GitHub 自动生成的 Source code ZIP**。

## EuLocation：卡包、小爱、搜索

默认组合：**卡包后台＋超级小爱＋搜索自动识别**。可以自定义选择卡包装／不装、小爱装／不装、搜索不装／自动／普通／折叠。音量键确认，30 秒无选择则取消；已管理的组件不能通过取消勾选来卸载，安装器会中止。

v0.6.0 从包中完整移除 MipayWallet 主 APK 和84个重复外置库。钱包主 APK 内已包含这些库；作为普通应用安装时由 Android 处理。四个卡包后台 MITSMClient、MINextpay、UPTsmService、PaymentService 及其所需载荷保留。原始 EU307 有 NFC、SecureElement 和设置入口相关代码，但不含这四个后台 APK，故不能把卡包支持整体删除。

**只用卡包不需要安装钱包主 APK。** 设置入口由 EU增强的“设置”作用域提供；EuLocation 提供后台，二者分工不同。真实支付、增删卡及所有金融业务不作为本次测试结论。

旧版升级：若模块中仍有钱包主 App，先停用该主 App。安装器校验其原 APK，在 root 私有目录 `/data/adb/eulocation-backups` 保存主 APK 和主 App 数据后，新模块不再挂载它。Android 在重启时可能清理该主 App 的活动数据；卡包后台数据、卡片设置和小爱录入不清理。备份是本机回退资料，不是可跨机恢复的支付卡备份。存在其它用户的钱包数据、未知旧 APK 或备份失败时中止。已有独立 `/data/app` 钱包不由模块启停或移除。

保留内部模块 ID `HyperOS3EU307Optional` 做原位升级；不再包含“钱包 App＋卡包”安装选项，也无开机钱包主 App 启停规则。不修改助手和 NFC 默认项，不自动接受协议、授权、登录或重录。

搜索含原签名普通／折叠 APK，仅提取所选版本。自动识别优先折叠属性，再参考 Fold／Flip 型号，支持手动覆盖。首次用原生偏好减少热榜、推荐和推广，后续不覆盖用户选择；不承诺所有联网内容无广告。Google 引擎、上滑和默认浏览器由 EU增强实现。普通版已在 EU307 使用；折叠版没有 EU 折叠机安装实测。

## EU增强：按应用提供兼容

| LSPosed 作用域 | 功能 |
| --- | --- |
| 录音机 com.android.soundrecorder | 进程内 CN 兼容，恢复应用录音；不是已验证的自动通话录音方案 |
| 设置 com.android.settings | 原生卡包入口，需 EuLocation 卡包后台 |
| 智能助理 com.miui.personalassistant | 负一屏出行助手入口及初始化，不代替本人授权 |
| 安装器 com.miui.packageinstaller | 局部系统应用更新解析兼容，保留签名、降级等检查；不关闭安全扫描 |
| 系统桌面 com.miui.home | EU307 桌面上滑原生搜索入口 |
| 搜索 com.android.quicksearchbox | Google 引擎及当前默认浏览器跳转，实测默认 Chrome |

旧用户需手动加入新推荐项；推荐不会覆盖已有作用域。其它用户选中的、通过系统标记及路径检查的应用沿用录音机式 CN 兼容；上述专用应用使用各自策略。**小米服务框架不在 EU增强处理，请交给超级岛。** 原独立负一屏和 system allow 的能力已并入 EU增强，不需要重复叠加。

## 超级岛 v0.18

LSPosed 按已安装应用勾选：淘宝闪购／饿了么 `me.ele`、滴滴 `com.sdu.didi.psnger`、系统界面 `com.android.systemui`、系统界面组件 `miui.systemui.plugin`、小米服务框架 `com.xiaomi.xmsf`。不要把 XMSF 同时交给 EU增强或其它区域 Hook。

保留 v0.15 的淘宝闪购前台原生推送服务启动方案及真实通知焦点适配。v0.16 修正待付款状态筛选，并增加限量、不含正文／订单／位置的诊断元数据。

v0.18 修复 EU307 的 **XMSF 6.2.67-G / code60002067** 在 China/CN 条件下的启用判断、国内推送主机和设备登记 URL。仅在精确包名／版本、MIUI 和 China 条件下生效，未知版本保持原行为；不改系统区域属性或清除登记缓存。使用原生小米通道，没有轮询、后台保活或模拟订单。

已实机验证：滴滴 SDK 和业务层推送 ID 从空恢复为有效，国内通道连接与绑定成功；恢复平时代理后重新连接及登记仍通过。**修复后尚未用新真实行程验证滴滴可见岛，不宣称滴滴端到端修复完成。** 淘宝闪购 v0.15 曾验证真实订单显示及送达更新；无需打开 App 的即时后台更新、长期稳定性、手环流转仍未证明。

## 测试与源码

EuLocation 的载荷逐文件哈希／CRC、迁移备份／拒绝条件、组件选择、搜索变体检查见 `tests`、`tools/verify_search_bundle.py`。超级岛策略测试见 `super-island/tests`。本轮刷入并重启后，用户确认无钱包主 App 的卡包首页及原有卡片正常，小爱桌面／锁屏均能沿用原录入唤醒；后台数据目录与默认项核对不变。详见 Release 说明。

源码目录：`module` 为 KSU 安装脚本，`eu-boost` 为 EU增强，`super-island` 为超级岛。OEM APK／库仅在对应命名附件中，未放入源码树；用户日志、ROM 镜像、私有备份和签名密钥不公开。其它机型、真实支付、全部小爱云端功能、耗电及 OTA 未验证。组件权利说明见 THIRD_PARTY_NOTICES.md。
