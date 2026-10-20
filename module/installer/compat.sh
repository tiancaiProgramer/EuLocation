check_profile() {
  [ "$BOOTMODE" = "true" ] || abort "! Running Android installation only."
  if [ -z "${KSU_VER_CODE:-}" ] && [ "${KSU:-false}" != "true" ]; then
    abort "! KernelSU required for this tested release."
  fi
  [ "$(getprop ro.product.device)" = "dada" ] || abort "! Expected Xiaomi 15 dada."
  [ "$(getprop ro.build.version.incremental)" = "OS3.0.307.0.WOCCNXM" ] || abort "! Exact EU307 build required."
  [ "$(getprop ro.product.mod_device)" = "dada_xiaomieu_global" ] || abort "! Expected xiaomi.eu dada."
  [ "$(getprop ro.build.version.sdk)" = "36" ] || abort "! Expected SDK 36."
  [ "$(getprop ro.product.cpu.abi)" = "arm64-v8a" ] || abort "! Expected arm64-v8a."
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
  unzip -p "$ZIPFILE" "payload/$COMPONENT/payload.sha256" > "$MANIFEST" || abort "! Component manifest missing."
  while read -r EXPECTED_HASH REL; do
    case "$REL" in
      *..*|/*|*' '*|'') abort "! Unsafe payload path." ;;
    esac
    case "$COMPONENT:$REL" in
      xiaoai:system/product/app/VoiceAssistAndroidT/*|xiaoai:system/product/app/VoiceTrigger/*) ;;
      wallet:system/product/app/MITSMClient/*|wallet:system/product/app/MipayWallet/*|wallet:system/product/app/MINextpay/*|wallet:system/product/app/UPTsmService/*|wallet:system/product/app/PaymentService/*|wallet:system.prop) ;;
      *) abort "! Unexpected component payload path." ;;
    esac
    case "$REL" in
      */*) mkdir -p "$MODPATH/${REL%/*}" ;;
    esac
    unzip -p "$ZIPFILE" "payload/$COMPONENT/$REL" > "$MODPATH/$REL" || abort "! Payload extraction failed."
  done < "$MANIFEST"
  (cd "$MODPATH" && sha256sum -c "reports/$COMPONENT.sha256") >&2 || abort "! Component integrity failed."
  cat "$MANIFEST" >> "$MODPATH/payload.sha256"
}

finish_install() {
  set_perm_recursive "$MODPATH" 0 0 0755 0644
  printf 'XIAOAI=%s\nWALLET=%s\n' "$XIAOAI" "$WALLET" > "$MODPATH/component-selection.conf"
  {
    echo "Build=$(getprop ro.build.version.incremental)"
    echo "AssistantBefore=$(settings get secure assistant)"
    echo "VoiceInteractionBefore=$(settings get secure voice_interaction_service)"
    echo "VoiceRecognitionBefore=$(settings get secure voice_recognition_service)"
    echo "PaymentDefaultBefore=$(settings get secure nfc_payment_default_component)"
    echo "PaymentForegroundBefore=$(settings get secure nfc_payment_foreground)"
    echo "DataCleanup=none"
    echo "UserSettingsModified=false"
    echo "WalletSEPropertyIncluded=$WALLET"
  } > "$MODPATH/reports/install-preflight.txt"
  ui_print "- Data, enrollment, cards, grants, assistant and NFC choices untouched."
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
