# Search payload selection and one-time native preference defaults.
# Original signed APKs remain unchanged. No account/consent/runtime grants.
search_payload_hash() {
  case "$1" in
    phone) echo c5c818831537b1edae9e50c8f2fb733068a8aeb9b07bfc2f000271d6f4efbc57 ;;
    fold) echo f988c9720c97aac9ae7e747879060c7e92cb5eb81c7f414e60373f2fe4c7d422 ;;
    *) return 1 ;;
  esac
}

check_search() {
  SEARCH_HASH="$(search_payload_hash "$SEARCH_MODE")" || abort "! Invalid search variant."
  SEARCH_PATHS="$(pm path --user 0 com.android.quicksearchbox 2>/dev/null)"
  [ -n "$SEARCH_PATHS" ] || return 0
  [ "$(printf '%s\n' "$SEARCH_PATHS" | wc -l | tr -d ' ')" = 1 ] || abort "! Split search package not supported."
  SEARCH_APK="${SEARCH_PATHS#package:}"
  case "$SEARCH_APK" in /data/app/*|/product/*|/system/*|/system_ext/*) ;; *) abort "! Unexpected search APK path." ;; esac
  [ -r "$SEARCH_APK" ] || abort "! Existing search APK is not visible."
  [ "$(sha256sum "$SEARCH_APK" | cut -d ' ' -f1)" = "$SEARCH_HASH" ] || abort "! Existing search differs from selected variant; no downgrade or data removal performed."
}

extract_search() {
  SEARCH_PREFIX="payload/search/$SEARCH_MODE"
  SEARCH_MANIFEST="$MODPATH/reports/search-source.sha256"
  unzip -p "$ZIPFILE" "$SEARCH_PREFIX/payload.sha256" > "$SEARCH_MANIFEST" || abort "! Search manifest missing."
  : > "$MODPATH/reports/search.sha256"
  while read -r EXPECTED_HASH REL; do
    case "$REL" in *..*|/*|*' '*|'') abort "! Unsafe search payload path." ;; esac
    case "$REL" in
      system/product/priv-app/MIUIQuickSearchBox/MIUIQuickSearchBox.apk|system/product/priv-app/MIUIQuickSearchBox/lib/arm64/*.so|system/product/etc/permissions/privapp-permissions-eulocation-search.xml) ;;
      *) abort "! Unexpected search payload path." ;;
    esac
    mkdir -p "$MODPATH/${REL%/*}"
    unzip -p "$ZIPFILE" "$SEARCH_PREFIX/$REL" > "$MODPATH/$REL" || abort "! Search extraction failed."
    printf '%s  %s\n' "$EXPECTED_HASH" "$REL" >> "$MODPATH/reports/search.sha256"
  done < "$SEARCH_MANIFEST"
  (cd "$MODPATH" && sha256sum -c reports/search.sha256) >&2 || abort "! Search integrity failed."
  cat "$MODPATH/reports/search.sha256" >> "$MODPATH/payload.sha256"
}

search_default_values() {
  printf '%s\n' common_hot=false common_recent_apps=false common_hint=false common_ad_block=true \
    enable_corpus_com.android.quicksearchbox/.xiaomi.XiaomiSearch=false
  if [ "$1" = phone ]; then
    printf '%s\n' app_suggestion_download_ad=false app_suggestion_game_box=false
  fi
}

search_awk() {
  if [ -x /data/adb/ksu/bin/busybox ]; then /data/adb/ksu/bin/busybox awk "$@"
  elif [ -x /data/adb/magisk/busybox ]; then /data/adb/magisk/busybox awk "$@"
  else awk "$@"; fi
}

# Transform known native boolean entries only, preserving all other XML text.
# A conflicting type, duplicate key or invalid root aborts before replacement.
search_defaults_transform() {
  case "$3" in phone|fold) ;; *) return 1 ;; esac
  search_awk -v defaults="$(search_default_values "$3" | tr '\n' ';')" '
    BEGIN { RS=">"; ORS=""; sub(/;$/, "", defaults); n=split(defaults, lines, ";");
      for(i=1;i<=n;i++){split(lines[i],kv,"="); keys[i]=kv[1];values[kv[1]]=kv[2];} }
    {
      if ($0 ~ /<map[[:space:]]*\/$/) {
        opened++; closed++; sub(/<map[[:space:]]*\/$/, "<map>"); printf "%s", $0;
        for(i=1;i<=n;i++)printf "\n<boolean name=\"%s\" value=\"%s\" />",keys[i],values[keys[i]];
        printf "\n</map>"; next;
      }
      if ($0 ~ /<map([[:space:]]|$)/) opened++;
      if (match($0,/name="[^"]+"/)) {
        key=substr($0,RSTART+6,RLENGTH-7);
        if(key in values){
          if($0 !~ /<boolean[[:space:]]/ || $0 !~ /value="(true|false)"/ || seen[key]++) { bad=1; exit 42; }
          sub(/value="(true|false)"/,"value=\"" values[key] "\"");
        }
      }
      if($0 ~ /<\/map[[:space:]]*$/){
        closed++;
        for(i=1;i<=n;i++)if(!seen[keys[i]])printf "\n<boolean name=\"%s\" value=\"%s\" />",keys[i],values[keys[i]];
      }
      if($0 ~ /</)printf "%s>",$0;else printf "%s",$0;
    }
    END { if(bad || opened!=1 || closed!=1)exit 42; }
  ' "$1" > "$2"
}

apply_search_defaults() {
  SEARCH_MODDIR="$1"
  SEARCH_CFG_MODE="$(grep -E '^SEARCH_MODE=(phone|fold)$' "$SEARCH_MODDIR/component-selection.conf" | cut -d= -f2)"
  case "$SEARCH_CFG_MODE" in phone|fold) ;; *) return 0 ;; esac
  SEARCH_DATA=/data/user/0/com.android.quicksearchbox
  SEARCH_MARKER="$SEARCH_DATA/files/eulocation_search_defaults_v1"
  [ ! -f "$SEARCH_MARKER" ] || return 0
  SEARCH_WAIT=0
  while [ "$SEARCH_WAIT" -lt 60 ]; do
    [ -d "$SEARCH_DATA" ] && [ "$(am get-started-user-state 0 2>/dev/null)" = RUNNING_UNLOCKED ] && break
    sleep 2; SEARCH_WAIT=$((SEARCH_WAIT + 1))
  done
  [ -d "$SEARCH_DATA" ] && [ "$(am get-started-user-state 0 2>/dev/null)" = RUNNING_UNLOCKED ] || return 1
  [ ! -L "$SEARCH_DATA" ] || return 1
  SEARCH_RUNTIME_APK="$(pm path --user 0 com.android.quicksearchbox 2>/dev/null)"
  SEARCH_RUNTIME_APK="${SEARCH_RUNTIME_APK#package:}"
  [ -r "$SEARCH_RUNTIME_APK" ] || return 1
  [ "$(sha256sum "$SEARCH_RUNTIME_APK" | cut -d ' ' -f1)" = "$(search_payload_hash "$SEARCH_CFG_MODE")" ] || return 1
  SEARCH_UID="$(stat -c %u "$SEARCH_DATA")"
  case "$SEARCH_UID" in ''|*[!0-9]*) return 1 ;; esac
  [ "$SEARCH_UID" -ge 10000 ] || return 1
  SEARCH_PREFDIR="$SEARCH_DATA/shared_prefs"
  [ ! -L "$SEARCH_PREFDIR" ] && [ ! -L "$SEARCH_DATA/files" ] || return 1
  SEARCH_CONTEXT="$(ls -Zd "$SEARCH_DATA" | search_awk '{print $1}')"
  case "$SEARCH_CONTEXT" in u:object_r:app_data_file:s0*|u:object_r:privapp_data_file:s0*) ;; *) return 1 ;; esac
  for SEARCH_DIR in "$SEARCH_PREFDIR" "$SEARCH_DATA/files"; do
    if [ ! -d "$SEARCH_DIR" ]; then
      mkdir "$SEARCH_DIR" && chown "$SEARCH_UID:$SEARCH_UID" "$SEARCH_DIR" && chmod 0700 "$SEARCH_DIR" && chcon "$SEARCH_CONTEXT" "$SEARCH_DIR" || return 1
    fi
  done
  mkdir -p "$SEARCH_MODDIR/reports" || return 1
  SEARCH_PREF="$SEARCH_PREFDIR/SearchSettings.xml"
  [ ! -L "$SEARCH_PREF" ] && [ ! -L "$SEARCH_MARKER" ] || return 1
  SEARCH_STOP_RESULT="$(am force-stop --user 0 com.android.quicksearchbox 2>&1)" || return 1
  printf '%s\n' "$SEARCH_STOP_RESULT" > "$SEARCH_MODDIR/reports/search-defaults-stop.txt"
  SEARCH_INPUT="$SEARCH_PREF"
  if [ -f "$SEARCH_PREF" ]; then
    cp -p "$SEARCH_PREF" "$SEARCH_MODDIR/reports/search-settings-before.xml" || return 1
    SEARCH_CONTEXT="$(ls -Zd "$SEARCH_PREF" | search_awk '{print $1}')"
  else
    SEARCH_CONTEXT="$(ls -Zd "$SEARCH_DATA" | search_awk '{print $1}')"
    SEARCH_INPUT="$SEARCH_MODDIR/reports/search-settings-empty.xml"
    printf '<?xml version="1.0" encoding="utf-8"?>\n<map>\n</map>\n' > "$SEARCH_INPUT" || return 1
  fi
  SEARCH_TEMP="$SEARCH_PREF.eulocation.$$"
  if ! search_defaults_transform "$SEARCH_INPUT" "$SEARCH_TEMP" "$SEARCH_CFG_MODE"; then
    rm -f "$SEARCH_TEMP"; return 1
  fi
  chown "$SEARCH_UID:$SEARCH_UID" "$SEARCH_TEMP" || return 1
  chmod 0600 "$SEARCH_TEMP" || return 1
  case "$SEARCH_CONTEXT" in u:object_r:app_data_file:s0*|u:object_r:privapp_data_file:s0*) chcon "$SEARCH_CONTEXT" "$SEARCH_TEMP" || return 1 ;; *) return 1 ;; esac
  mv -f "$SEARCH_TEMP" "$SEARCH_PREF" || return 1
  SEARCH_MARKER_TEMP="$SEARCH_MARKER.tmp.$$"
  printf 'policy=1\nvariant=%s\n' "$SEARCH_CFG_MODE" > "$SEARCH_MARKER_TEMP" || return 1
  chown "$SEARCH_UID:$SEARCH_UID" "$SEARCH_MARKER_TEMP" && chmod 0600 "$SEARCH_MARKER_TEMP" && chcon "$SEARCH_CONTEXT" "$SEARCH_MARKER_TEMP" || return 1
  mv -f "$SEARCH_MARKER_TEMP" "$SEARCH_MARKER" || return 1
  printf 'Policy=1\nVariant=%s\nNativePreferencesInitialized=true\nRepeatedBootOverride=false\n' "$SEARCH_CFG_MODE" > "$SEARCH_MODDIR/reports/search-defaults.txt"
}
