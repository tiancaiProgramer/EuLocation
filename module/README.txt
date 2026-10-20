EU307 localization test release / Xiaomi 15 dada / Android 16
Exact target: xiaomi.eu OS3.0.307.0.WOCCNXM. KernelSU + existing mount metamodule.

Optional bundle menu:
1. XiaoAI = previously tested original-signed core plus voice wake, one item.
2. Wallet = original-signed five-component wallet stack, one item.
For each item: Volume UP installs; Volume DOWN skips. 30-second timeout aborts.
No choices are silently accepted. Both skipped aborts without changing old modules.
For explicit authorized ADB deployment only, a strict root-owned 0600 one-time
options file can select the items instead of hardware keys. It is consumed on
success. It is parsed as data, never executed/sourced.

Skipping does NOT uninstall independent components or erase data. This release
refuses to drop an already bundled component by deselection on update: keep it
selected, or use an explicitly planned rollback/removal. Failed preflight leaves
the existing active module(s) untouched. The installer does not disable old
modules; disable only the selected components' old modules AFTER success and
before reboot. The current managed ADB migration performs this step separately.
Retain old modules disabled for rollback. Never enable duplicate payload modules.

Wallet EU307 adaptation is a compatibility rebuild, NOT a claimed CN307 payload:
TSMClient/Nextpay/UPTsm/PaymentService originate from CN dada305; MipayWallet is
the previously verified Xiaomi-signed release. Same 98 binary payload files as
the working v1.1 stack on this EU307 device. New installer checks EU307, eSE/HCE,
the existing NFC service and currently installed APK identities, and refuses
silent downgrade/replacement of data-app updates. Existing ro.se.type and the
legacy wallet marker property are carried forward byte-identically ONLY when
Wallet is selected. No user NFC default/payment-role changes are made.

XiaoAI payload is byte-identical to merged v0.2: same app IDs, original signatures,
mount paths and native libraries. Wallet and XiaoAI do not replace one another.
No AIService/AICR/vision/speech/vendor/DSP replacement, global region spoof,
SELinux/signature override, permission/profile rewrite, ART/cache or data cleanup.
No voice samples, voiceprint/enrollment, card/account databases, payment keys or
credentials are included, copied or exported. Do not re-enroll merely to migrate.

KernelSU App Profile visibility requirements remain unchanged: android, NFC,
wallet stack, voiceassist and voicetrigger must see their own mounted APKs.
Do not grant these apps root or turn off module hiding globally as a shortcut.

Testing is limited to package/payload, UI, service/grant/default preservation,
existing card visibility, and user-spoken desktop/locked-screen wakeup. It does
NOT prove bank transactions, secure-element provisioning or long-term power use.
No real payment, card addition/deletion, bank login or biometric confirmation
should be automated as an installation test.

Rollback: disable this new module, re-enable the retained old modules for the
same components, reboot. App data/settings remain. Before removing all assistant
or wallet providers, choose an appropriate alternative default yourself. No
automatic re-enable/reset is performed by uninstall. Disable test modules before OTA.
