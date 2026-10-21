# EuLocation v0.4.2 + CN Hook v0.7 · 去机型限制测试版

本次 Release 同时提供 KernelSU ZIP 与独立 LSPosed APK。

- **移除机型/完整版本号绑定**：安装器、开机服务、设置卡包 Hook 不再锁定 dada / EU307。保留 HyperOS 3 xiaomi.eu、Android 16 arm64、硬件能力、可信 APK 和载荷校验。取消锁定不等于已适配所有手机。
- **刷入可选**：超级小爱（核心+语音唤醒）可独立选择；卡片服务可选完整钱包、仅卡包或不安装。没有静默默认全选，超时取消。
- **仅卡包**：新安装不包含钱包主 App；已有钱包迁移保留原 APK 作为数据载体并停用主 App，保留既有卡片/设置。不是直接删除钱包 APK。
- CN Hook 默认推荐仍只有录音机，作用域按需手动勾选：
  - 录音机 `com.android.soundrecorder` → **应用录音**，不宣传为已验证的通话录音。
  - 小米服务框架 `com.xiaomi.xmsf` → **超级岛前置需求**；使用独立 Global/SG 兼容路径，不是通用 CN 伪装。
  - 设置 `com.android.settings` → **设置里的卡包入口**，需兼容卡片后台。不是勾选系统框架 `android`。
- 超级岛第三方业务适配器不在此次附件中；XMSF 是前置条件，不代表任意 App 自动上岛。

## 测试状态

实机基线：**小米 15 / dada，xiaomi.eu HyperOS 3 OS3.0.307.0.WOCCNXM，Android 16，KernelSU + Hybrid Mount**。此前 v0.4.1 + CN Hook 0.6 已由用户确认：仅卡包首页及原卡正常，小爱桌面/锁屏沿用旧录入唤醒正常。

**v0.4.2 / CN Hook 0.7 只完成主机回归、模拟不同机型/版本判定、编译签名与载荷校验，未进行新的手机安装回归。** 其他机型/ROM、真实交易、开删卡、充值/迁移、银行认证、长期耗电与 OTA 未验证。载荷沿用原 v0.3，不是新增提取的 CN307 应用。

请下载下列命名附件并核对 `SHA256SUMS.txt`，不要刷 Source code ZIP。CN Hook APK 不是 KSU 模块，需安装后在 LSPosed 中启用并选择作用域。ZIP 则通过 KernelSU 刷入。原模块 ID/Hook 包名及签名保留，以便原位升级。

有钱包数据载体的迁移在彻底移除前应先恢复完整模式；停用/卸载模块不能保证 Android 永不清理缺失系统包的数据。不要清数据、删卡、重新录入或以交易作为安装测试。详细安装、挂载可见性和回退风险见仓库 README。
