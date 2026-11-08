runtime_profile_matches() {
  # Platform guard, not a device/codename or full-build allowlist.
  case "$(getprop ro.build.version.incremental)" in OS3.*) ;; *) return 1 ;; esac
  case "$(getprop ro.product.mod_device)" in *_xiaomieu_global|*_xiaomieu) ;; *) return 1 ;; esac
  [ "$(getprop ro.build.version.sdk)" = "36" ] || return 1
  [ "$(getprop ro.product.cpu.abi)" = "arm64-v8a" ] || return 1
}

check_profile() {
  [ "$BOOTMODE" = "true" ] || abort "! Running Android installation only."
  if [ -z "${KSU_VER_CODE:-}" ] && [ "${KSU:-false}" != "true" ]; then
    abort "! KernelSU required for this tested release."
  fi
  runtime_profile_matches || abort "! HyperOS 3 xiaomi.eu / Android 16 / arm64-v8a required (other models unverified)."
  [ -d /data/adb/metamodule ] || abort "! Existing mount metamodule required."
  [ ! -f /data/adb/metamodule/disable ] || abort "! Mount metamodule disabled."
  [ ! -f /data/adb/metamodule/remove ] || abort "! Mount metamodule pending removal."
}

check_existing() {
  PKG="$1"; EXPECTED_PATH="$2"; EXPECTED_HASH="$3"
  CURRENT_PATH="$(pm path "$PKG" 2>/dev/null)"
  if [ -n "$CURRENT_PATH" ]; then
    [ "$CURRENT_PATH" = "package:$EXPECTED_PATH" ] || abort "! Different/data-app version of $PKG detected; refusing silent downgrade."
    [ -r "$EXPECTED_PATH" ] || abort "! Installed APK invisible: $PKG"
    [ "$(sha256sum "$EXPECTED_PATH" | cut -d ' ' -f 1)" = "$EXPECTED_HASH" ] || abort "! Installed $PKG differs from tested payload."
  fi
}

check_xiaoai() {
  pm list libraries | grep -q '^library:vendor.xiaomi.hardware.misys.V3_0$' || abort "! Required misys library absent."
  pm path com.xiaomi.mibrain.speech 2>/dev/null | grep -q '^package:' || abort "! Existing speech engine required."
  check_existing com.miui.voiceassist /product/app/VoiceAssistAndroidT/VoiceAssistAndroidT.apk c741871d14462ffcc4d29be283610f51ec0bdec569b3f40e0fee570ab00f9895
  check_existing com.miui.voicetrigger /product/app/VoiceTrigger/VoiceTrigger.apk 8f61cfd4282198f43a752b42bcf320db354d279f3f0f760bfba090e8ad76f512
}

check_wallet() {
  pm list features | grep -q '^feature:android.hardware.nfc.ese$' || abort "! eSE capability missing."
  pm list features | grep -q '^feature:android.hardware.nfc.hce$' || abort "! HCE capability missing."
  pm path com.android.nfc 2>/dev/null | grep -q '^package:' || abort "! Existing EU NFC service required."
  check_existing com.miui.tsmclient /product/app/MITSMClient/MITSMClient.apk c79df91c96734bcd0085ec19e74e55abce7be55b7a7a8429b18ec73cdb4332c5
  check_existing com.miui.nextpay /product/app/MINextpay/MINextpay.apk 169626141c4f2ae0ce4a53552111e59f935151c49361948d09fbd9a9d341139f
  check_existing com.unionpay.tsmservice.mi /product/app/UPTsmService/UPTsmService.apk ac52313450813ab118bd291013e6094b1db3c86468a5899343b4135b40b80c83
  check_existing com.xiaomi.payment /product/app/PaymentService/PaymentService.apk 2b978506f41450e745dc7044c7086a6693e991d2a47b5f94464aa65994f49bcd
}

extract_component() {
  COMPONENT="$1"
  MANIFEST="$MODPATH/reports/$COMPONENT.sha256"
  ALL_MANIFEST="$MODPATH/reports/$COMPONENT-source.sha256"
  unzip -p "$ZIPFILE" "payload/$COMPONENT/payload.sha256" > "$ALL_MANIFEST" || abort "! Component manifest missing."
  : > "$MANIFEST"
  while read -r EXPECTED_HASH REL; do
    case "$REL" in
      *..*|/*|*' '*|'') abort "! Unsafe payload path." ;;
    esac
    case "$COMPONENT:$REL" in
      xiaoai:system/product/app/VoiceAssistAndroidT/*|xiaoai:system/product/app/VoiceTrigger/*) ;;
      wallet:system/product/app/MITSMClient/*|wallet:system/product/app/MINextpay/*|wallet:system/product/app/UPTsmService/*|wallet:system/product/app/PaymentService/*|wallet:system.prop) ;;
      *) abort "! Unexpected component payload path." ;;
    esac
    case "$REL" in
      */*) mkdir -p "$MODPATH/${REL%/*}" ;;
    esac
    unzip -p "$ZIPFILE" "payload/$COMPONENT/$REL" > "$MODPATH/$REL" || abort "! Payload extraction failed."
    printf '%s  %s\n' "$EXPECTED_HASH" "$REL" >> "$MANIFEST"
  done < "$ALL_MANIFEST"
  (cd "$MODPATH" && sha256sum -c "reports/$COMPONENT.sha256") >&2 || abort "! Component integrity failed."
  cat "$MANIFEST" >> "$MODPATH/payload.sha256"
}

prepare_wallet_split() {
  # The main App is no longer a payload. Card backends have separate package
  # identities/data. Never uninstall, clear, or change a separately installed App.
  KEEP_WALLET_MAIN=0
  WALLET_UI_MANAGED=0
  WALLET_ORIGINAL_ENABLED=0
  OLD_WALLET=/data/adb/modules/HyperOS3EU307Optional/system/product/app/MipayWallet/MipayWallet.apk
  [ -f "$OLD_WALLET" ] || return 0
  [ "$WALLET_MODE" = cards ] || abort "! Keep card services selected during wallet split migration."
  [ "$(sha256sum "$OLD_WALLET" | cut -d ' ' -f1)" = ce369489bd4cf5262c0e35403566be2274a06bbeadb265fc8afe2897750db800 ] || abort "! Unknown legacy Wallet APK; migration refused."
  WALLET_CURRENT="$(pm path --user 0 com.mipay.wallet 2>/dev/null)"
  case "$WALLET_CURRENT" in
    package:/data/app/*) ui_print "- Separate Wallet installation is untouched."; return 0 ;;
    package:/product/app/MipayWallet/MipayWallet.apk|'') ;;
    *) abort "! Unexpected Wallet layout; refusing migration." ;;
  esac
  [ "$(am get-started-user-state 0 2>/dev/null)" = RUNNING_UNLOCKED ] || abort "! Unlock user 0 before legacy Wallet backup."
  # Refuse multi-user migration rather than silently lose a second user's App data.
  for WALLET_USERDIR in /data/user/*/com.mipay.wallet /data/user_de/*/com.mipay.wallet; do
    [ -d "$WALLET_USERDIR" ] || continue
    case "$WALLET_USERDIR" in /data/user/0/*|/data/user_de/0/*) ;; *) abort "! Wallet has secondary-user data; back up/migrate separately first." ;; esac
    [ ! -L "$WALLET_USERDIR" ] || abort "! Wallet data symlink refused."
  done
  BACKUP_ROOT=/data/adb/eulocation-backups
  [ ! -L "$BACKUP_ROOT" ] || abort "! Unsafe backup directory."
  mkdir -p "$BACKUP_ROOT" || abort "! Cannot create private backup root."
  chmod 0700 "$BACKUP_ROOT" || abort "! Cannot protect backup root."
  WALLET_BACKUP="$BACKUP_ROOT/wallet-main-$(date +%Y%m%d-%H%M%S)-$$"
  mkdir "$WALLET_BACKUP" && chmod 0700 "$WALLET_BACKUP" || abort "! Cannot create private Wallet backup."
  cp "$OLD_WALLET" "$WALLET_BACKUP/MipayWallet.apk" || abort "! Wallet APK backup failed."
  [ "$(sha256sum "$WALLET_BACKUP/MipayWallet.apk" | cut -d ' ' -f1)" = ce369489bd4cf5262c0e35403566be2274a06bbeadb265fc8afe2897750db800 ] || abort "! Wallet APK backup integrity failed."
  WALLET_DATA_PATHS=""
  for WALLET_DATA in user/0/com.mipay.wallet user_de/0/com.mipay.wallet; do
    [ ! -d "/data/$WALLET_DATA" ] || WALLET_DATA_PATHS="$WALLET_DATA_PATHS $WALLET_DATA"
  done
  if [ -n "$WALLET_CURRENT" ]; then
    WALLET_ENABLED="$(dumpsys package com.mipay.wallet | sed -n 's/.*User 0:.* enabled=\([0-4]\) .*/\1/p' | head -n1)"
    [ "$WALLET_ENABLED" = 3 ] || abort "! Disable legacy Wallet main App before migration; card services stay enabled."
  fi
  if [ -n "$WALLET_DATA_PATHS" ]; then
    (cd /data && tar -czf "$WALLET_BACKUP/wallet-main-data.tar.gz" $WALLET_DATA_PATHS) || abort "! Wallet private-data backup failed."
    tar -tzf "$WALLET_BACKUP/wallet-main-data.tar.gz" >/dev/null || abort "! Wallet backup is unreadable."
  fi
  chmod 0600 "$WALLET_BACKUP"/* || abort "! Cannot protect Wallet backup."
  ui_print "- Legacy Wallet main APK/data backed up privately: $WALLET_BACKUP"
  ui_print "- Main App leaves system overlay at reboot; Android may remove its active data."
  ui_print "- Card backends, card data and settings are not removed."
}

finish_install() {
  set_perm_recursive "$MODPATH" 0 0 0755 0644
  printf 'XIAOAI=%s\nWALLET=%s\n' "$XIAOAI" "$WALLET" > "$MODPATH/component-selection.conf"
  printf 'WALLET_MODE=%s\nKEEP_WALLET_MAIN=%s\nWALLET_UI_MANAGED=%s\nWALLET_ORIGINAL_ENABLED=%s\n' \
    "$WALLET_MODE" "$KEEP_WALLET_MAIN" "$WALLET_UI_MANAGED" "$WALLET_ORIGINAL_ENABLED" >> "$MODPATH/component-selection.conf"
  printf 'SEARCH_MODE=%s\nSEARCH_REQUESTED=%s\nSEARCH_DETECT_REASON=%s\n' "${SEARCH_MODE:-none}" "${SEARCH_REQUESTED:-none}" "${SEARCH_DETECT_REASON:-explicit-selection}" >> "$MODPATH/component-selection.conf"
  set_perm "$MODPATH/service.sh" 0 0 0755
  {
    echo "Build=$(getprop ro.build.version.incremental)"
    echo "AssistantBefore=$(settings get secure assistant)"
    echo "VoiceInteractionBefore=$(settings get secure voice_interaction_service)"
    echo "VoiceRecognitionBefore=$(settings get secure voice_recognition_service)"
    echo "PaymentDefaultBefore=$(settings get secure nfc_payment_default_component)"
    echo "PaymentForegroundBefore=$(settings get secure nfc_payment_foreground)"
    echo "ModuleDataCleanup=none"
    echo "LegacyWalletMain=private backup then overlay removal; Android may clean its data"
    echo "DefaultRolesModified=false"
    echo "WalletMode=$WALLET_MODE"
    echo "WalletMainEnabledStateManaged=$WALLET_UI_MANAGED"
    echo "WalletSEPropertyIncluded=$WALLET"
  } > "$MODPATH/reports/install-preflight.txt"
  ui_print "- No card/backend/enrollment cleanup; assistant and NFC defaults untouched."
  ui_print "- Wallet main App is a separate optional APK; no boot-time enable/disable policy."
  ui_print "- KSU App Profile module visibility is required; not changed globally."
  ui_print "- After success, disable ONLY old modules for selected components:"
  if [ "$XIAOAI" = "1" ]; then
    ui_print "  HyperOS3XiaoAIFullDada307 (and any older XiaoAI pair)"
  fi
  if [ "$WALLET" = "1" ]; then
    ui_print "  HyperOS3WalletDada305"
    INSTALL_ID="$(sed -n 's/^id=//p' "$MODPATH/module.prop")"
    [ "$INSTALL_ID" = "HyperOS3WalletDada307" ] || ui_print "  HyperOS3WalletDada307 (if present)"
  fi
  ui_print "- Keep old modules installed but disabled for rollback; then reboot."
  ui_print "- Payment/card transactions are not an installation test."
}
