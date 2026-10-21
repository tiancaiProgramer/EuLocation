# EuLocation（测试版）【hyper eu 本地化模块】

[下载 Releases](https://github.com/tiancaiProgramer/EuLocation/releases)

本仓库发布两个独立文件：

| 文件 | 用途 |
| --- | --- |
| `EuLocation-v0.4.2-model-unlocked-test.zip` | KernelSU 模块：超级小爱、完整钱包或仅卡包，可分别选择 |
| `PerAppCN-v0.7-model-unlocked-test.apk` | LSPosed CN Hook：录音机应用录音、小米服务框架超级岛前置适配、设置卡包入口 |

不是 GPS/定位模块，也不包含超级岛第三方业务适配器。不要把 GitHub 自动生成的 Source code ZIP 当作可刷模块。

## 机型限制与测试范围

v0.4.2 / CN Hook 0.7 已移除 **dada 机型白名单和 OS3.0.307.0.WOCCNXM 完整版本号绑定**。保留的环境条件是 HyperOS 3（`OS3.*`）、xiaomi.eu（`mod_device` 后缀 `_xiaomieu_global` 或 `_xiaomieu`）、Android 16 / SDK 36、arm64-v8a。CN Hook 中这一平台检查只约束设置卡包适配，原有录音机及 XMSF 路径不因此新增平台限制。

**取消机型限制不等于全机型适配。** 不替换 vendor/DSP，不绕过 NFC/eSE/HCE、语音依赖、已安装应用路径/哈希和载荷完整性检查；硬件不满足或已有应用版本不同仍会中止。请勿为通过检查清数据、删卡或强制降级。

实际手机验证基线：**小米 15 / dada，xiaomi.eu HyperOS 3 OS3.0.307.0.WOCCNXM，Android 16 / arm64，KernelSU 32601 + Hybrid Mount 6.2.3**。基线版本为 EuLocation v0.4.1 + CN Hook 0.6，用户确认：

- 设置 → 更多设置 → 卡包：首页及原有卡片正常，钱包主 App 停用时仍能进入。
- 沿用原有唤醒录入，桌面和锁屏均可唤醒超级小爱，无需重录。
- 迁移未清数据、重置默认助手/支付方式或重新授权。

**本次去限制的新版本仅完成主机测试、编译、签名和载荷校验，尚未重新安装到手机回归。其他机型/ROM 未实测。** 真实支付、开删卡、卡迁移/充值、银行认证、全部小爱云端能力、长期耗电和 OTA 均未验证。首页正常不代表金融业务均可用。

## CN Hook 的功能与勾选范围

安装 APK，在兼容现代 libxposed API 的 LSPosed 框架中启用，按需要勾选，随后重启。

| 需求 | CN Hook 必须勾选 | 实际作用及边界 |
| --- | --- | --- |
| **应用录音** | **录音机 `com.android.soundrecorder`** | 对该系统 App 提供进程内 CN 兼容。不是已验证的通话自动录音方案，也不绕过录音权限或保证任意 App 音频可捕获 |
| **超级岛前置需求** | **小米服务框架 `com.xiaomi.xmsf`** | 本项目既有超级岛场景所需前置项。G 版服务框架使用独立的 Global/SG 推送适配，**不是对 XMSF 套通用 CN 属性**；匹配已核验的 6.2.x-G 方法结构，结构不符不处理 |
| **设置内添加/恢复卡包** | **设置 `com.android.settings`** | 恢复原生卡包项并交给 TSM 打开。需 EuLocation 钱包/仅卡包后端、可信 TSM APK 和功能位 1024；不是勾选「系统框架 android」 |

**默认推荐项保持只有录音机**。XMSF、设置需手动按需添加，不会自动全选。通用 CN 路径可由用户选择其他系统 App，但仅对带系统标记且安装于系统分区的包生效；普通 `/data/app` 应用即使勾选也不处理。`system_server` 不 Hook。

设置使用窄范围 Hook，不做整个设置进程的 CN 伪装，不替换 Settings.apk，不改变全局地区或默认支付方式。保留「扩展功能」中的钱包开关意愿；原生控制器/方法缺失时不会凭空生成相应系统 UI。

### 超级岛不是只勾选 XMSF 就全部完成

CN Hook 的 XMSF 项是前置条件，**不是所有第三方 App 的上岛适配器**，不能保证每单、每个 App 都上岛。本 Release 不附带此前单独开发的超级岛业务适配 APK，也没有新增真实订单回归结果。

已有业务适配器的作用域与 CN Hook 分开：淘宝闪购/饿了么 `me.ele`、滴滴 `com.sdu.didi.psnger`、系统界面 `com.android.systemui`、系统界面组件 `miui.systemui.plugin` 属于**另一个业务适配器**的配置。保留原本已工作的业务配置，不要把这四项照搬到 CN Hook，也不需要为此给 CN Hook 勾选电话、电信服务或整个系统框架。

## EuLocation 安装菜单

不是默认强制小爱+钱包，也不是只能二选一：

1. 超级小爱（核心+语音唤醒）：音量上安装，音量下跳过。
2. 卡片服务：音量上安装，音量下跳过。
3. 如选卡片服务：音量上为**完整钱包**，音量下为**仅卡包**。

每次等待 30 秒，超时取消；全部跳过取消。可以只装小爱、只装钱包/卡包，或两者一起。安装器要求 KernelSU 和已启用的挂载元模块，不会自动安装它们。

更新时不能靠取消选择来卸载本模块已经管理的组件；这会中止安装。独立旧模块不会被跳过选项删除。内部模块 ID 保持 `HyperOS3EU307Optional` 以便原位升级，它是历史标识，不是机型限制。

### 完整钱包与仅卡包

- **完整钱包**：钱包主 App + TSMClient、Nextpay、UPTsm、PaymentService。
- **仅卡包新安装**：设备原先没有钱包主 App 时，不解包/挂载 MipayWallet，仅装四个卡片后台及钱包属性。
- **已有钱包迁移到仅卡包**：保留原签名的钱包 APK/库作为数据载体，仅对 user 0 停用钱包主 App；不是物理删除 APK 的迁移。卡片后台继续工作。
- 切回完整钱包时，仅恢复本模块曾管理的钱包启用状态。此往返分支已做模拟测试，未新增实机往返测试。
- 仅卡包需 CN Hook 勾选设置以恢复入口；eID 等依赖钱包主 App 的业务可能需要完整钱包。

不包含用户卡片/账号数据库、支付密钥、语音样本或个人备份，不清理数据，不重置权限、助手、支付默认项。开机服务仅管理模块负责的钱包主 App 启用状态，遇到包管理器启动延迟会重试。

**已有钱包的数据载体迁移，彻底移除前应先恢复完整模式。** 仅停用或卸载模块不会自动恢复缺失的系统 APK，也不能保证 Android 永不清理消失的包数据。先准备兼容备份和救援办法；不要用删卡、清数据或支付交易来验证安装。

## 安装与挂载

1. 下载对应附件与 `SHA256SUMS.txt`，核对 SHA-256；已有 CN Hook 用户可原位更新 APK，签名沿用旧版本。
2. KernelSU 管理器刷入 ZIP，按上述菜单选择。确认成功后再停用覆盖同一 APK 的旧小爱/钱包模块，避免重复挂载；保留自己的兼容备份。
3. 对相关进程关闭「卸载模块 / Umount modules」，使其可见模块挂载。这与 LSPosed 的作用域是两回事；不需要授予应用 Root 或全局取消隐藏。
4. CN Hook 根据表格勾选录音机/XMSF/设置，重启并解锁。
5. 查看卡包首页、既有卡片和原唤醒录入是否保留，不进行支付、开删卡或无故重录。首次安装的权限、协议、默认助手及唤醒录入由本人选择。

挂载可见性涉及系统/NFC 相关进程及所选组件，设置进程也需能读取 TSM 后端。相关包：

- 小爱：`com.miui.voiceassist`、`com.miui.voicetrigger`。
- 卡片服务：`com.miui.tsmclient`、`com.miui.nextpay`、`com.unionpay.tsmservice.mi`、`com.xiaomi.payment`；完整钱包/数据载体另含 `com.mipay.wallet`。
- 系统依赖：`android`、`com.android.nfc`（共享 UID 配置按实际管理器处理）、`com.android.settings`。

高级部署支持 root 所有、0600、严格三行的一次性选择文件；不执行其中的代码，发布包不附带默认双选文件。正常管理器刷入仍按键选择。

## 载荷来源与回退

载荷与原 v0.3 完全一致：小爱核心 VoiceAssistAndroidT 7.12.2.0318 + VoiceTrigger v-8.0.0.30-xiaomi；钱包 TSMClient 26.04.27.1.f、MipayWallet 6.111.0.5701.2736、MINextpay 26.01.30.1、UPTsmService 01.00.57、PaymentService 2.9.8。

钱包来自此前 CN dada305 组件及已核验的小米签名钱包版本，**不声称来自 CN307**。选钱包/卡包时保留 `ro.se.type=eSE,HCE,UICC` 与既有模块标记；不是全局 CN 区域伪装。不放宽签名、SELinux，不替换 AIService、语音引擎、vendor/DSP，跨机型能力仍依赖实际系统。

遇到启动问题先用预备的模块停用/救援办法，避免重复覆盖；涉及已有钱包数据载体时遵守上文迁移提醒。卸载脚本不清数据，但不会自动更换默认助手、重新启用旧模块或保证系统的缺包数据保留行为。OTA 前先停用测试模块，升级后重新检查兼容性，不能因取消版本锁而直接认定可用。

## 源码与本地验证

`module/` 与本次 ZIP 的安装脚本和元数据逐字节一致；`cn-hook/` 为本次 APK 对应 Java/Gradle 源码。专有 APK/库仅在 Release ZIP 中，不提交到 Git；见 [第三方说明](THIRD_PARTY_NOTICES.md)。没有上传设备日志、个人备份或签名私钥。

```sh
python tools/verify_release.py /path/to/EuLocation-v0.4.2-model-unlocked-test.zip --apk /path/to/PerAppCN-v0.7-model-unlocked-test.apk
sh tests/test_selection.sh
python tests/test_runtime.py
```

Windows 主机可通过 `BASH_BIN` 指定 bash。给运行测试设置 `RELEASE_ZIP` 和 `PAYLOAD_SOURCE_ZIP`（旧 v0.3 ZIP）可额外比较全部载荷；未提供时该项明确跳过。Java 平台判定用 `tests/PlatformProfileTest.java` 与 `PlatformProfile.java` 单独编译运行。主机模拟测试不代表真实硬件、实体音量键或交易验证。

`tools/build_bundle.py` 接受固定哈希的 v0.3 ZIP 与一个尚不存在的输出 ZIP 路径，重打包时保留全部专有载荷；CN Hook 可用 Gradle/Android SDK 编译（AGP 8.7.3、JDK 17、compileSdk 35、libxposed API 102.0.0）。自行编译签名与发布签名不同，不能直接覆盖安装官方附件；不要为了换签清理已有应用数据。
