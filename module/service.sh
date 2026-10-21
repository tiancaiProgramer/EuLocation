#!/system/bin/sh
# Manage only the module-owned Wallet UI enabled state. Never clear data/defaults.
MODDIR=${0%/*}
. "$MODDIR/installer/compat.sh"
runtime_profile_matches || exit 0
WAIT=0
while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$WAIT" -lt 180 ]; do
  sleep 1
  WAIT=$((WAIT + 1))
done
[ "$(getprop sys.boot_completed)" = 1 ] || exit 0
[ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ] || exit 0
CONFIG="$MODDIR/component-selection.conf"
MODE="$(grep -E '^WALLET_MODE=(cards|full|none)$' "$CONFIG" | cut -d= -f2)"
MANAGED="$(sed -n 's/^WALLET_UI_MANAGED=\([01]\)$/\1/p' "$CONFIG")"
ORIGINAL="$(sed -n 's/^WALLET_ORIGINAL_ENABLED=\([0-4]\)$/\1/p' "$CONFIG")"
[ "$MANAGED" = 1 ] || exit 0
pm path com.mipay.wallet 2>/dev/null | grep -q '^package:/product/app/MipayWallet/MipayWallet.apk$' || exit 0
run_wallet_action() {
  ATTEMPT=0
  while [ "$ATTEMPT" -lt 30 ]; do
    # Binder shell commands must not inherit a private /data/adb output FD.
    # Capture through a pipe first, then let our root shell write the report.
    if RESULT="$(pm "$1" --user 0 com.mipay.wallet 2>&1)"; then
      printf '%s\n' "$RESULT" > "$MODDIR/reports/wallet-ui-service.txt"
      echo "Action=$1 Attempts=$((ATTEMPT + 1))" >> "$MODDIR/reports/wallet-ui-service.txt"
      return 0
    fi
    printf '%s\n' "$RESULT" > "$MODDIR/reports/wallet-ui-service.txt"
    ATTEMPT=$((ATTEMPT + 1))
    sleep 2
  done
  return 1
}
if [ "$MODE" = cards ]; then
  run_wallet_action disable-user
elif [ "$MODE" = full ]; then
  case "$ORIGINAL" in
    0) ACTION=default-state ;; 1) ACTION=enable ;; 2) ACTION=disable ;;
    3) ACTION=disable-user ;; 4) ACTION=disable-until-used ;; *) exit 0 ;;
  esac
  if run_wallet_action "$ACTION"; then
    sed -i 's/^WALLET_UI_MANAGED=1$/WALLET_UI_MANAGED=0/' "$CONFIG"
  fi
fi
