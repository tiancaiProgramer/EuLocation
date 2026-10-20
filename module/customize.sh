SKIPUNZIP=1
ASH_STANDALONE=1
REPLACE=""
ui_print "[EU307 optional localization v0.3]"
unzip -o "$ZIPFILE" module.prop README.txt uninstall.sh 'installer/*' payload-provenance.json -d "$MODPATH" >&2 || abort "! Installer extraction failed."
. "$MODPATH/installer/selection.sh"
. "$MODPATH/installer/compat.sh"
check_profile
OPTIONS_FILE=/data/local/tmp/HyperOS3EU307-install-options.conf
USE_OPTIONS=0
if [ -f "$OPTIONS_FILE" ]; then
  [ "$(stat -c %u "$OPTIONS_FILE")" = "0" ] || abort "! ADB options must be root-owned."
  [ "$(stat -c %a "$OPTIONS_FILE")" = "600" ] || abort "! ADB options must have mode 600."
  parse_options "$OPTIONS_FILE" || abort "! Invalid/empty ADB component selection."
  USE_OPTIONS=1
  ui_print "- Explicit ADB selection: XiaoAI=$XIAOAI Wallet=$WALLET"
else
  ui_print "Each component is independent. Skipping an independent module leaves it alone."
  ui_print "Already bundled components cannot be removed by deselection in this release."
  select_component "1/2: XiaoAI (core + voice wake)"
  XIAOAI="$CHOICE"
  select_component "2/2: Xiaomi Wallet"
  WALLET="$CHOICE"
  [ "$XIAOAI$WALLET" != "00" ] || abort "! Nothing selected. Existing modules and data unchanged."
fi
preserve_managed_components /data/adb/modules/HyperOS3EU307Optional/component-selection.conf
[ "$XIAOAI" = "0" ] || check_xiaoai
[ "$WALLET" = "0" ] || check_wallet
mkdir -p "$MODPATH/reports"
: > "$MODPATH/payload.sha256"
[ "$XIAOAI" = "0" ] || extract_component xiaoai
[ "$WALLET" = "0" ] || extract_component wallet
finish_install
if [ "$USE_OPTIONS" = "1" ]; then
  # Consume only this exact root-owned, validated, task-created options file.
  mv "$OPTIONS_FILE" "$MODPATH/reports/adb-selection-input.conf" || abort "! Could not consume ADB options."
fi
