EuLocation v0.5.0 optional-search test: HyperOS 3 xiaomi.eu, Android 16, arm64.
No codename/full-build allowlist. Baseline v0.4.1 + CN Hook 0.6 was tested only
on Xiaomi 15 dada OS3.0.307.0.WOCCNXM; this revision is host-tested, not yet
device-regression-tested. Other models/builds are experimental, not certified.
Hardware, installed-package path/hash and payload-integrity checks remain.
KernelSU + an existing enabled mount metamodule are required.

Installation starts with UP=use defaults, DOWN=customize.
Defaults: cards only + XiaoAI core/wake + native Search (automatic variant).
Custom groups: 1) cards only / wallet App+cards / none; 2) XiaoAI on/off;
3) Search off / auto / manually choose ordinary phone or foldable.
Auto uses ro.embedded.fold.type, power_foldable, multi-display flags, then model
or market names containing Fold/Flip. Otherwise ordinary-phone fallback.
The detected variant and reason are printed and stored in component-selection.conf.
Both original-signed APKs are in the ZIP; only the chosen variant is extracted.
Every prompt times out after 30 seconds and cancels, including defaults confirmation.
ADB options accept SEARCH_MODE=none|auto|phone|fold as the optional fourth line;
legacy three-line input keeps Search disabled for backward compatibility.
Native Search defaults disable hot rankings, app recommendations, promoted hints,
Xiaomi curated web source, and phone download/game promotions once, preserving
local app indexing, consent, permissions and unrelated preferences. Later user
changes are retained. This does not promise every online page is advertising-free.
Google engine option requires EUBoost v0.10.0 with Search (com.android.quicksearchbox) scoped in LSPosed. Native APKs/signatures and current engine choice stay unchanged.
Native swipe-up Search entry requires the same EUBoost version with Miui Home scoped; currently guarded to the verified EU307 Home APK.
Search systemless mounting and foldable use still require device regression tests.

Already managed components cannot be removed by skipping; no data deletion UI.

Cards-only retains TSMClient, Nextpay, UPTsm and PaymentService unchanged. On a
fresh device without MipayWallet it does not extract/mount MipayWallet at all.
When migrating an existing Wallet installation, its original-signed APK is kept
mounted to preserve package/data identity, and the main package is disabled for
user 0. This migration is NOT a physically APK-free installation. No app data,
card databases, enrollment, grants, NFC/default payment or assistant choices are
cleared/reset. Full mode restores only the enabled state previously managed by
this module. For manual installs, the boot service applies enabled state after
boot completes; controlled ADB migration disables the main app before reboot.
Package-manager startup failures are retried up to 30 times, every two seconds.

Restore full mode before uninstalling a migrated data carrier. Disabling/removing
the module does not itself restore APKs or guarantee Android retains data of a
missing system package. Keep your own compatible backup; avoid destructive tests.

The native Settings entry requires Per-App CN v0.7 with com.android.settings scoped.
Its narrow hook checks the runtime platform, trusted TSM APK hash and feature bit
1024, and changes preference availability/title/click. It does not set global CN
properties, replace Settings.apk, change payment defaults or automate payments.
Non-Settings hooks in the original CN module remain unchanged.

Wallet/card payloads remain byte-identical to v0.3: CN305 components plus the
previously verified signed MipayWallet release, not claimed CN307 app payloads.
eID and other wallet-dependent operations may need full Wallet. No real payment,
card creation/deletion/transfer/recharge or bank identity operation is automated.
The device owner must confirm UI/card preservation, not infer it from a launch.

Retain selected apps' KSU mount visibility. Do not grant these apps root or hide
modules globally. Do not enable duplicate payload modules. Disable test modules
before OTA; passing the guards does not prove compatibility on other devices.
