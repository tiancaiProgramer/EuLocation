#!/bin/sh
# Host-side data/parser tests. Key events are MOCKED, not physical key testing.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TEST_DIR=$(mktemp -d)
ui_print() { :; }
abort() { echo "EXPECTED_ABORT: $*" >&2; exit 97; }
. "$ROOT/module/installer/selection.sh"
PASS=0
for PAIR in 11 10 01; do
  EXPECT_X="${PAIR%?}"
  EXPECT_W="${PAIR#?}"
  if [ "$EXPECT_W" = 1 ]; then EXPECT_MODE=full; else EXPECT_MODE=none; fi
  printf 'BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=%s\nWALLET_MODE=%s\n' "$EXPECT_X" "$EXPECT_MODE" > "$TEST_DIR/valid.conf"
  parse_options "$TEST_DIR/valid.conf"
  [ "$XIAOAI:$WALLET_MODE" = "$EXPECT_X:$EXPECT_MODE" ]
  PASS=$((PASS+1))
done
printf 'BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=0\nWALLET_MODE=none\n' > "$TEST_DIR/empty.conf"
if parse_options "$TEST_DIR/empty.conf"; then exit 1; fi
PASS=$((PASS+1))
printf 'BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=1;echo BAD\nWALLET_MODE=full\n' > "$TEST_DIR/invalid.conf"
if parse_options "$TEST_DIR/invalid.conf"; then exit 1; fi
PASS=$((PASS+1))
printf 'BUNDLE_ID=WRONG\nXIAOAI=1\nWALLET_MODE=full\n' > "$TEST_DIR/wrong-id.conf"
if parse_options "$TEST_DIR/wrong-id.conf"; then exit 1; fi
PASS=$((PASS+1))
printf 'XIAOAI=1\nWALLET=1\n' > "$TEST_DIR/old-selection.conf"
if (XIAOAI=0; WALLET_MODE=cards; preserve_managed_components "$TEST_DIR/old-selection.conf"); then exit 1; fi
PASS=$((PASS+1))
if (XIAOAI=1; WALLET_MODE=none; preserve_managed_components "$TEST_DIR/old-selection.conf"); then exit 1; fi
PASS=$((PASS+1))
XIAOAI=1; WALLET_MODE=full
preserve_managed_components "$TEST_DIR/old-selection.conf"
PASS=$((PASS+1))
timeout() { shift; "$@"; }
getevent() { [ -z "$MOCK_KEY" ] || printf '/dev/input/mock EV_KEY %s DOWN\n' "$MOCK_KEY"; }
MOCK_KEY=KEY_VOLUMEUP
select_component mock-up
[ "$CHOICE" = "1" ]
PASS=$((PASS+1))
MOCK_KEY=KEY_VOLUMEDOWN
select_component mock-down
[ "$CHOICE" = "0" ]
PASS=$((PASS+1))
MOCK_KEY=''
if (select_component mock-timeout); then exit 1; fi
PASS=$((PASS+1))
echo "SELECTION_TESTS_PASSED=$PASS/12 (mocked keys)"
echo "Only generated test configs remain at: $TEST_DIR"
