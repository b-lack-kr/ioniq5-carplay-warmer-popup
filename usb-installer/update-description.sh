#!/system/bin/sh
# Update only descriptive lines of an already registered HMG-API NEMESIS item.
# Must run after all installs and sync have completed, while NEMESIS is idle.
set -f
umask 077
APP_ROOT=/data/nemesis/apps
MOD_ROOT=/data/hmgapi/mods
BACKUP_ROOT=/data/nemesis/neev-description-backups
case "$1" in
 neev-usb-carplay) short='Wireless CarPlay with USB Wi-Fi. by blackr.'; detail='Wireless CarPlay with a compatible 5 GHz USB Wi-Fi adapter and navigation guidance. by blackr.';;
 neev-warmer-mapless-research) short='WARMER popup over CarPlay. by blackr.'; detail='Displays vehicle seat heating and ventilation controls over CarPlay. by blackr.';;
 precondition-star) short='Add a preconditioning favorite. by blackr.'; detail='Adds the preconditioning shortcut to the vehicle favorite button. by blackr.';;
 neev-carplay-mute-pause) short='Pause and resume music with vehicle mute. by blackr.'; detail='Pauses CarPlay music on mute and resumes when sound returns. by blackr.';;
 *) echo UNKNOWN_ITEM; exit 2;;
esac
id=$1
version=$2
case "$version" in ''|*[!0-9.]*) echo BAD_VERSION; exit 2;; esac
app=$APP_ROOT/hmgapi.$id
mod=$MOD_ROOT/$id
file=$app/NEMESIS.data
[ -d "$app" ] && [ ! -L "$app" ] && [ -f "$file" ] && [ ! -L "$file" ] && [ -d "$mod" ] && [ ! -L "$mod" ] && [ -f "$mod/mod.json" ] && [ ! -L "$mod/mod.json" ] || { echo NOT_REGISTERED; exit 1; }
# Only HMG-API generated listings: a real NEMESIS package with the same id
# owns its own metadata and must not be overwritten.
[ -f "$app/registered" ] && [ ! -L "$app/registered" ] && [ -d "$app/mod" ] && [ ! -L "$app/mod" ] && [ -f "$app/mod/id" ] && [ ! -L "$app/mod/id" ] && [ "$(cat "$app/mod/id")" = "$id" ] || { echo NOT_GENERATED_LISTING; exit 1; }
grep -F 'Listed here by HMG-API' "$app/registered" >/dev/null || { echo NOT_GENERATED_LISTING; exit 1; }
# Restrict operation to the current matching registered mod/version.
[ "$(grep -c '^id=hmgapi\.' "$file")" = 1 ] && grep -F -x "id=hmgapi.$id" "$file" >/dev/null || { echo WRONG_ID; exit 1; }
[ "$(grep -c '^package-version=' "$file")" = 1 ] && grep -F -x "package-version=$version" "$file" >/dev/null || { echo WRONG_PACKAGE_VERSION; exit 1; }
[ "$(grep -c '^short-desc=' "$file")" = 1 ] && [ "$(grep -c '^detailed-desc=' "$file")" = 1 ] || { echo BAD_FIELDS; exit 1; }
[ "$(grep -c '"version"[[:space:]]*:' "$mod/mod.json")" = 1 ] && grep '"version"[[:space:]]*:' "$mod/mod.json" | grep -F "\"$version\"" >/dev/null || { echo WRONG_MOD_VERSION; exit 1; }
[ ! -L "$BACKUP_ROOT" ] && mkdir -p "$BACKUP_ROOT" || { echo BACKUP_DIR_ERROR; exit 1; }
lock=$app/.blackr-description-lock
mkdir "$lock" 2>/dev/null || { echo BUSY; exit 1; }
tmp=$app/.NEMESIS.data.description.$$
backup=$BACKUP_ROOT/$id.$version.$$.data
cleanup() { rm -f "$tmp"; rmdir "$lock" 2>/dev/null || :; }
trap cleanup EXIT HUP INT TERM
[ ! -e "$tmp" ] && [ ! -L "$tmp" ] && [ ! -e "$backup" ] && [ ! -L "$backup" ] || { echo BAD_TEMP; exit 1; }
cp -p "$file" "$backup" && cp -p "$file" "$tmp" || { echo BACKUP_FAILED; exit 1; }
md5val() { val=$(md5 "$1") || return 1; echo "${val%% *}"; }
original=$(md5val "$file") || exit 1
[ "$(md5val "$backup")" = "$original" ] || { echo BACKUP_MISMATCH; exit 1; }
# Android 4.4 on this head unit has no sed or printf. Refuse inputs whose
# bytes shell echo could interpret, or an unterminated final line. This keeps
# every unrelated line unchanged instead of silently mangling a descriptor.
(
 while :; do
  line=
  if ! IFS= read -r line; then
   [ -z "$line" ] || exit 1
   break
  fi
  case "$line" in *\\*|-n|-e|-E) exit 1;; esac
  case "$line" in
   short-desc=*) echo "short-desc=\"$short\"";;
   detailed-desc=*) echo "detailed-desc=\"$detail\"";;
   *) echo "$line";;
  esac
 done
) < "$backup" > "$tmp" || { echo WRITE_FAILED; exit 1; }
grep -F -x "short-desc=\"$short\"" "$tmp" >/dev/null && grep -F -x "detailed-desc=\"$detail\"" "$tmp" >/dev/null || { echo WRITE_MISMATCH; exit 1; }
# Abort if descriptor changed since its backup; no other fields are rewritten.
[ "$(md5val "$file")" = "$original" ] || { echo CONCURRENT_CHANGE; exit 1; }
# The head unit's Android toolbox mv accepts only source and destination.
# Both paths are in the same directory; rename replaces the regular file.
mv "$tmp" "$file" || { echo REPLACE_FAILED; exit 1; }
echo UPDATED
