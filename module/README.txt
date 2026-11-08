EuLocation v0.6.0 slim test
Test baseline: Xiaomi 15 dada / xiaomi.eu OS3.0.307.0.WOCCNXM / Android 16 arm64,
KernelSU + enabled mount metamodule. Other models/builds unverified.

Three independent selections, all default on:
1. Card backends, without the Wallet main App or its 84 duplicate native libraries.
2. XiaoAI core + voice wake.
3. Native Search: auto-detect phone/fold, with manual override.
The unchanged signed Wallet APK is a separate optional download. Do not install
it for Settings card access alone. EUBoost Settings scope supplies the card entry.
No boot-time Wallet enable/disable policy.

Legacy upgrade: first disable the old module-owned Wallet main App. Its original
APK and main-App data are privately archived under /data/adb/eulocation-backups
(root-only), then the old Wallet overlay disappears at reboot. Android may remove
that main App active data. Card backend data/settings, XiaoAI enrollment, Search
preferences and assistant/NFC defaults are not cleared. These private archives
are rollback material, not portable payment-card backups. Secondary-user Wallet
data or an unknown legacy APK cancels migration. Separate data-app Wallet is
untouched. Deselecting an existing managed component cancels instead of removing it.

Original signed phone/fold Search APKs, one variant extracted. Initial native
preference defaults reduce recommendations once; user choices are retained.
Fold Search is not device-tested on an EU foldable. EUBoost supplies desktop swipe,
Google engine and current default-browser compatibility.
No payments, card creation/removal or enrollment is part of installation tests.
Keep a working old ZIP and private backups. Use named release assets, not GitHub
Source code ZIP. Hardware/platform/payload hash guards remain.
