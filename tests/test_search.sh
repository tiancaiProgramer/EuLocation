#!/system/bin/sh
set -eu
BASE="$1"
. "$BASE/installer/selection.sh"
. "$BASE/installer/search.sh"
ui_print() { :; }
abort() { echo "ABORT:$*" >&2; exit 97; }
PASS=0
getprop() {
  case "$MOCK:$1" in
    hardware:ro.embedded.fold.type) echo 2 ;;
    bool:persist.sys.power_foldable) echo true ;;
    multidisplay:persist.sys.muiltdisplay_type) echo 2 ;;
    name:ro.product.marketname) echo 'Xiaomi MIX Fold 3' ;;
    flip:ro.product.model) echo 'MIX Flip 2' ;;
    phone:ro.product.model) echo 'Xiaomi 15' ;;
  esac
}
for MOCK in hardware bool multidisplay name flip phone; do
  detect_search_variant
  if [ "$MOCK" = phone ]; then EXPECTED=phone; else EXPECTED=fold; fi
  [ "$SEARCH_DETECTED" = "$EXPECTED" ]
  PASS=$((PASS+1))
done
recommended_selection
[ "$XIAOAI:$WALLET_MODE:$SEARCH_MODE" = 1:cards:auto ]; PASS=$((PASS+1))
MOCK=hardware; resolve_search_selection; [ "$SEARCH_MODE" = fold ]; PASS=$((PASS+1))
SEARCH_MODE=phone; resolve_search_selection; [ "$SEARCH_MODE" = phone ]; PASS=$((PASS+1))
for MODE in auto phone fold none; do
  printf 'BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=1\nWALLET_MODE=cards\nSEARCH_MODE=%s\n' "$MODE" > "$BASE/options.conf"
  parse_options "$BASE/options.conf"; [ "$SEARCH_MODE" = "$MODE" ]; PASS=$((PASS+1))
done
printf 'BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=0\nWALLET_MODE=none\nSEARCH_MODE=fold\n' > "$BASE/options.conf"
parse_options "$BASE/options.conf"; PASS=$((PASS+1))
printf 'BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=1\nWALLET_MODE=cards\nSEARCH_MODE=auto;bad\n' > "$BASE/options.conf"
if parse_options "$BASE/options.conf"; then exit 1; fi; PASS=$((PASS+1))
printf 'XIAOAI=1\nWALLET=1\nSEARCH_MODE=phone\n' > "$BASE/old.conf"
if (XIAOAI=1; WALLET_MODE=cards; SEARCH_MODE=none; preserve_managed_components "$BASE/old.conf"); then exit 1; fi
PASS=$((PASS+1))
for MODE in phone fold; do
  search_defaults_transform "$BASE/input.xml" "$BASE/$MODE.xml" "$MODE"
  search_defaults_transform "$BASE/$MODE.xml" "$BASE/$MODE-again.xml" "$MODE"
  cmp "$BASE/$MODE.xml" "$BASE/$MODE-again.xml"; PASS=$((PASS+1))
done
for BAD in duplicate type malformed; do
  if search_defaults_transform "$BASE/$BAD.xml" "$BASE/rejected.xml" phone; then exit 1; fi
  PASS=$((PASS+1))
done
echo "SEARCH_TESTS_PASS=$PASS"
