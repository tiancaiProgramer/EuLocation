#!/system/bin/sh
# Initialize native Search preferences once. Wallet main App is independently managed.
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
# One-time native Search preferences. Existing user choices remain after setup.
if [ -f "$MODDIR/installer/search.sh" ]; then
  . "$MODDIR/installer/search.sh"
  apply_search_defaults "$MODDIR" || echo "Search defaults deferred/failed; see reports." > "$MODDIR/reports/search-defaults-error.txt"
fi
