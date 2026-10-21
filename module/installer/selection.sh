# Selection parsing is data-only. Never source an external options file.
parse_options() {
  [ -r "$1" ] || return 1
  [ "$(wc -l < "$1" | tr -d ' ')" = "3" ] || return 1
  [ "$(grep -c '^BUNDLE_ID=HyperOS3EU307Optional$' "$1")" = "1" ] || return 1
  [ "$(grep -c '^XIAOAI=[01]$' "$1")" = "1" ] || return 1
  [ "$(grep -Ec '^WALLET_MODE=(none|full|cards)$' "$1")" = "1" ] || return 1
  XIAOAI="$(sed -n 's/^XIAOAI=\([01]\)$/\1/p' "$1")"
  WALLET_MODE="$(sed -n 's/^WALLET_MODE=//p' "$1")"
  [ "$XIAOAI:$WALLET_MODE" != "0:none" ]
}

select_component() {
  ui_print ""
  ui_print "$1"
  ui_print "Volume UP = install/update; Volume DOWN = skip independent component"
  ui_print "Select within 30 seconds. Timeout cancels, never selects silently."
  KEY_WAIT=0
  while [ "$KEY_WAIT" -lt 30 ]; do
    KEY_EVENT="$(timeout 1 getevent -ql 2>/dev/null | grep -m 1 -E 'KEY_VOLUME(UP|DOWN)[[:space:]]+DOWN')"
    case "$KEY_EVENT" in
      *KEY_VOLUMEUP*) CHOICE=1; return 0 ;;
      *KEY_VOLUMEDOWN*) CHOICE=0; return 0 ;;
    esac
    KEY_WAIT=$((KEY_WAIT + 1))
  done
  abort "! No choice received. Installation cancelled; existing modules/data untouched."
}

preserve_managed_components() {
  OLD_SELECTION="$1"
  [ -f "$OLD_SELECTION" ] || return 0
  if [ "$XIAOAI" = "0" ] && grep -q '^XIAOAI=1$' "$OLD_SELECTION"; then
    abort "! XiaoAI is already managed by this bundle. Deselecting is not removal. Keep selected; no change made."
  fi
  if [ "$WALLET_MODE" = "none" ] && grep -q '^WALLET=1$' "$OLD_SELECTION"; then
    abort "! Wallet is already managed by this bundle. Deselecting is not removal. Keep selected; no change made."
  fi
}
