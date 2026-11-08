# Selection parsing is data-only. Never source an external options file.
parse_options() {
  [ -r "$1" ] || return 1
  OPTION_LINES="$(wc -l < "$1" | tr -d ' ')"
  case "$OPTION_LINES" in 3|4) ;; *) return 1 ;; esac
  [ "$(grep -c '^BUNDLE_ID=HyperOS3EU307Optional$' "$1")" = "1" ] || return 1
  [ "$(grep -c '^XIAOAI=[01]$' "$1")" = "1" ] || return 1
  [ "$(grep -Ec '^WALLET_MODE=(none|cards)$' "$1")" = "1" ] || return 1
  XIAOAI="$(sed -n 's/^XIAOAI=\([01]\)$/\1/p' "$1")"
  WALLET_MODE="$(sed -n 's/^WALLET_MODE=//p' "$1")"
  SEARCH_MODE=none
  if [ "$OPTION_LINES" = 4 ]; then
    [ "$(grep -Ec '^SEARCH_MODE=(none|auto|phone|fold)$' "$1")" = 1 ] || return 1
    SEARCH_MODE="$(sed -n 's/^SEARCH_MODE=//p' "$1")"
  fi
  [ "$XIAOAI:$WALLET_MODE:$SEARCH_MODE" != "0:none:none" ]
}

select_component() {
  ui_print ""
  ui_print "$1"
  ui_print "${2:-Volume UP = install/update; Volume DOWN = skip independent component}"
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
  SEARCH_MODE=${SEARCH_MODE:-none}
  [ -f "$OLD_SELECTION" ] || return 0
  if [ "$XIAOAI" = "0" ] && grep -q '^XIAOAI=1$' "$OLD_SELECTION"; then
    abort "! XiaoAI is already managed by this bundle. Deselecting is not removal. Keep selected; no change made."
  fi
  if [ "$SEARCH_MODE" = none ] && grep -Eq '^SEARCH_MODE=(phone|fold)$' "$OLD_SELECTION"; then
    abort "! Search is already managed. Keep it selected; deselection is not removal."
  fi
  if [ "$WALLET_MODE" = "none" ] && grep -q '^WALLET=1$' "$OLD_SELECTION"; then
    abort "! Wallet is already managed by this bundle. Deselecting is not removal. Keep selected; no change made."
  fi
}

# Prefer explicit hardware properties. Market/model names cover devices whose
# vendor does not expose those properties; codenames are deliberately not a list.
detect_search_variant() {
  SEARCH_DETECTED=phone
  SEARCH_DETECT_REASON=ordinary-phone-fallback
  case "$(getprop ro.embedded.fold.type)" in
    1|2|3) SEARCH_DETECTED=fold; SEARCH_DETECT_REASON=ro.embedded.fold.type; return 0 ;;
  esac
  case "$(getprop persist.sys.power_foldable)" in
    true|1) SEARCH_DETECTED=fold; SEARCH_DETECT_REASON=persist.sys.power_foldable; return 0 ;;
  esac
  case "$(getprop persist.sys.muiltdisplay_type)" in
    2|3) SEARCH_DETECTED=fold; SEARCH_DETECT_REASON=persist.sys.muiltdisplay_type; return 0 ;;
  esac
  SEARCH_MODEL_NAMES="$(getprop ro.product.marketname) $(getprop ro.product.vendor.marketname) $(getprop ro.product.model) $(getprop ro.product.vendor.model) $(getprop ro.product.odm.model)"
  if printf '%s' "$SEARCH_MODEL_NAMES" | grep -Eiq '(^|[^a-z])(fold|flip)([0-9]|[^a-z]|$)'; then
    SEARCH_DETECTED=fold; SEARCH_DETECT_REASON=fold-flip-model-name
  fi
}

resolve_search_selection() {
  SEARCH_REQUESTED="$SEARCH_MODE"
  if [ "$SEARCH_MODE" = auto ]; then
    detect_search_variant
    SEARCH_MODE="$SEARCH_DETECTED"
    ui_print "- Search auto detection: $SEARCH_MODE ($SEARCH_DETECT_REASON)"
  else
    SEARCH_DETECT_REASON=explicit-selection
  fi
}

recommended_selection() {
  XIAOAI=1
  WALLET_MODE=cards
  SEARCH_MODE=auto
}
