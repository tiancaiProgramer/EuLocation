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
  check_existing com.mipay.wallet /product/app/MipayWallet/MipayWallet.apk ce369489bd4cf5262c0e35403566be2274a06bbeadb265fc8afe2897750db800
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
      wallet:system/product/app/MITSMClient/*|wallet:system/product/app/MipayWallet/*|wallet:system/product/app/MINextpay/*|wallet:system/product/app/UPTsmService/*|wallet:system/product/app/PaymentService/*|wallet:system.prop) ;;
      *) abort "! Unexpected component payload path." ;;
    esac
    if [ "$COMPONENT" = "wallet" ] && [ "$WALLET_MODE" = "cards" ] && [ "$KEEP_WALLET_MAIN" = "0" ]; then
      case "$REL" in system/product/app/MipayWallet/*) continue ;; esac
    fi
    case "$REL" in
      */*) mkdir -p "$MODPATH/${REL%/*}" ;;
    esac
    unzip -p "$ZIPFILE" "payload/$COMPONENT/$REL" > "$MODPATH/$REL" || abort "! Payload extraction failed."
    printf '%s  %s\n' "$EXPECTED_HASH" "$REL" >> "$MANIFEST"
  done < "$ALL_MANIFEST"
  (cd "$MODPATH" && sha256sum -c "reports/$COMPONENT.sha256") >&2 || abort "! Component integrity failed."
  cat "$MANIFEST" >> "$MODPATH/payload.sha256"
}

prepare_wallet_ui_state() {
  KEEP_WALLET_MAIN=0
  WALLET_UI_MANAGED=0
  WALLET_ORIGINAL_ENABLED=0
  OLD_CONFIG=/data/adb/modules/HyperOS3EU307Optional/component-selection.conf
  if [ "$WALLET_MODE" != "none" ] && pm path com.mipay.wallet 2>/dev/null | grep -q '^package:'; then
    KEEP_WALLET_MAIN=1
    WALLET_ORIGINAL_ENABLED="$(dumpsys package com.mipay.wallet | sed -n 's/.*User 0:.* enabled=\([0-4]\) .*/\1/p' | head -n 1)"
    case "$WALLET_ORIGINAL_ENABLED" in [0-4]) ;; *) abort "! Cannot determine original Wallet enabled state." ;; esac
  fi
  if [ -f "$OLD_CONFIG" ] && grep -q '^WALLET_UI_MANAGED=1$' "$OLD_CONFIG"; then
    WALLET_ORIGINAL_ENABLED="$(sed -n 's/^WALLET_ORIGINAL_ENABLED=\([0-4]\)$/\1/p' "$OLD_CONFIG")"
    case "$WALLET_ORIGINAL_ENABLED" in [0-4]) ;; *) abort "! Invalid managed Wallet state." ;; esac
    WALLET_UI_MANAGED=1
  fi
  if [ "$WALLET_MODE" = "cards" ] && [ "$KEEP_WALLET_MAIN" = "1" ]; then
    WALLET_UI_MANAGED=1
    ui_print "- Existing Wallet APK retained DISABLED as a data carrier; no data removal."
  fi
}

finish_install() {
  set_perm_recursive "$MODPATH" 0 0 0755 0644
  printf 'XIAOAI=%s\nWALLET=%s\n' "$XIAOAI" "$WALLET" > "$MODPATH/component-selection.conf"
  printf 'WALLET_MODE=%s\nKEEP_WALLET_MAIN=%s\nWALLET_UI_MANAGED=%s\nWALLET_ORIGINAL_ENABLED=%s\n' \
    "$WALLET_MODE" "$KEEP_WALLET_MAIN" "$WALLET_UI_MANAGED" "$WALLET_ORIGINAL_ENABLED" >> "$MODPATH/component-selection.conf"
  set_perm "$MODPATH/service.sh" 0 0 0755
  {
    echo "Build=$(getprop ro.build.version.incremental)"
    echo "AssistantBefore=$(settings get secure assistant)"
    echo "VoiceInteractionBefore=$(settings get secure voice_interaction_service)"
    echo "VoiceRecognitionBefore=$(settings get secure voice_recognition_service)"
    echo "PaymentDefaultBefore=$(settings get secure nfc_payment_default_component)"
    echo "PaymentForegroundBefore=$(settings get secure nfc_payment_foreground)"
    echo "DataCleanup=none"
    echo "DefaultRolesModified=false"
    echo "WalletMode=$WALLET_MODE"
    echo "WalletMainEnabledStateManaged=$WALLET_UI_MANAGED"
    echo "WalletSEPropertyIncluded=$WALLET"
  } > "$MODPATH/reports/install-preflight.txt"
  ui_print "- No data/enrollment/card cleanup; assistant and NFC defaults untouched."
  ui_print "- Cards mode manages only Wallet main-app enabled state, not card backends."
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
