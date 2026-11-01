# EuLocation v0.5.0 · EU增强 v0.10.1 · 超级岛 v0.15（EU307 测试版）

测试机：**小米 15 / dada，xiaomi.eu HyperOS 3 OS3.0.307.0.WOCCNXM，Android 16 / arm64**。其它 EU 版本／机型未实测。本 Release 提供三个独立附件，不是合并成一个 APK。

| 附件 | 安装位置 | 用途 |
| --- | --- | --- |
| `EuLocation-v0.5.0-search-test.zip` | KernelSU 刷入 | 小爱核心＋唤醒、完整钱包／仅卡包、普通／折叠版搜索，可分别选择 |
| `EUBoost-v0.10.1-browser-fix-test.apk` | 安装并在 LSPosed 启用 | 原 CN Hook，负责应用录音、设置卡包、负一屏、安装兼容、桌面上滑搜索与 Google 引擎／默认浏览器跳转 |
| `Island-v0.15-native-push-start-test.apk` | 安装并在 LSPosed 启用 | 淘宝闪购／饿了么、滴滴和系统通知的超级岛适配；补齐淘宝闪购前台原生推送服务启动 |

## EuLocation 安装选项

默认组合：**仅卡包＋超级小爱＋搜索自动识别**。也可自定义：钱包不装／仅卡包／钱包 App＋卡包；小爱开／关；搜索不装／自动／手动普通／手动折叠。音量键确认，30 秒超时取消。折叠识别优先系统标记，再参考 Fold／Flip 型号名；ZIP 仅提取选中的版本。

搜索首次默认关闭热榜、首页推荐、提示词、搜索精选来源，以及普通版下载／游戏推广，保留本机应用搜索和后续用户选择。**折叠版只核验载荷、配置和机型识别，尚未在 EU 折叠机安装实测。**

已有钱包转仅卡包仍保留原 APK 为数据载体并停用主 App；没有清卡片、语音录入或其它用户数据，不改助手／支付默认项。

## EU增强 LSPosed 作用域

- 录音机 `com.android.soundrecorder` → 应用录音 CN 兼容；不是已验证的通话录音方案。
- 设置 `com.android.settings` → 卡包入口，需 EuLocation 卡片后台。
- 智能助理 `com.miui.personalassistant` → 负一屏出行助手入口及原生初始化。
- 安装器 `com.miui.packageinstaller` → 局部系统应用更新解析兼容，保留签名等系统检查。
- 系统桌面 `com.miui.home` → 桌面上滑原生搜索（当前核验 EU307 桌面）。
- 搜索 `com.android.quicksearchbox` → Google 引擎选项、Google 查询跳转当前默认浏览器。

**EU增强不再处理小米服务框架；不要把 XMSF 加回 EU增强。** 桌面、搜索等推荐项需在旧用户作用域手动勾选，推荐不会覆盖原勾选。

## 超级岛 LSPosed 作用域

按已安装业务 App 保留 `me.ele`、`com.sdu.didi.psnger`，以及 `com.android.systemui`、`miui.systemui.plugin`；兼容保留 `com.xiaomi.xmsf`。**v0.15 的 XMSF 是 no-op，不改 CN／Global 或方法；不能把这个勾选本身宣传为修复原因。**

v0.15 在淘宝闪购前台恢复时请求启动应用自身原生 MiPush 服务，无定时轮询、后台保活和模拟订单。用户已确认真实订单可见岛及已送达更新；整机冷启／无需打开应用的后台恢复未验证。滴滴沿用已有适配，本次未新增真实订单回归；手环同步未通过验证。

## 已验证及边界

已确认：原生菜单可选 Google；上滑打开原生搜索；录音机可本机搜索并打开；Google 可跳转当前默认 Chrome 并显示结果页。其它默认浏览器未逐一实测。助手／NFC 默认项及仅卡包停用状态保留。

保留硬件、平台、APK 版本与哈希检查，不保证任意机型／应用都兼容。真实支付、卡片业务、全部小爱云端能力、长期耗电、OTA 未验证。旧重复 KSU 载荷模块和旧独立负一屏／system allow 配置应按 README 迁移，保留备份；不清数据、删卡或重录来“修复”。

详见仓库 README 的用途表及三组作用域。核对 `SHA256SUMS.txt`，不要刷 GitHub Source code ZIP。源码中不包含 OEM APK、用户日志、私有签名或账号数据。
