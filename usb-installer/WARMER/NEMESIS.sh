#!/system/bin/sh
# Local research candidate: never modifies maps, engine binaries, or the star mod.
export PATH=/system/bin:/system/xbin:/data/nemesis/bin
umask 077
USB=${0%/*}; [ "$USB" != "$0" ] || USB=$PWD
USB=$(cd "$USB" && pwd -P) || exit 2
case "$USB" in /|/system|/system/*|/data|/data/*) exit 2;; esac
SRC="$USB/WARMER_MAPLESS/payload"
OUT="$USB/WARMER_MAPLESS_RESULT_$(date +%Y%m%d_%H%M%S)_$$"
mkdir "$OUT" || exit 2
exec > "$OUT/INSTALL.log" 2>&1
fail(){ echo "$1"; echo "$1" > "$OUT/STATUS.txt"; sync; exit 1; }
[ "$(getprop ro.build.product)" = NEEV.KOR.0000.V039.001.260902 ] || fail UNSUPPORTED_PRODUCT
[ "$(getprop ro.board.platform)" = tcc803x ] || fail UNSUPPORTED_PLATFORM
case "$(id)" in 'uid=0('*|'uid=0 '*|'uid=0') ;; *) fail ROOT_REQUIRED;; esac
[ -d /data/nemesis ] && [ ! -L /data/nemesis ] || fail NEMESIS_MISSING
[ -x /system/bin/app_process.hmgapi-stock ] && [ -f /system/framework/hmgapi-hook.jar ] || fail HMGAPI_MISSING
[ -d "$SRC" ] && [ ! -L "$SRC" ] || fail PAYLOAD_MISSING
[ ! -e /data/hmgapi/mods/neev-warmer-async-research ] || fail OTHER_WARMER_RESEARCH_INSTALLED
for file in mod.json settings.json hooks.jar META-HMGAPI/manifest.json META-HMGAPI/signature; do
 [ -f "$SRC/$file" ] && [ ! -L "$SRC/$file" ] || fail INVALID_PAYLOAD
done
cli() (
 unset LD_PRELOAD HMGAPI_SELFTEST HMGAPI_ROOT HMGAPI_HOST HMGAPI_PRODUCT HMGAPI_ONEBIN HMGAPI_PROPS HMGAPI_TEST_ANY_CAR HMGAPI_LPATCH HMGAPI_HMGAPID
 if [ -z "${BOOTCLASSPATH:-}" ]; then while read command name value; do [ "$command" != export ] || export "$name=$value"; done < /init.environ.rc; fi
 export CLASSPATH=/system/framework/hmgapi-hook.jar ANDROID_ROOT=/system ANDROID_DATA=/data
 exec /system/bin/app_process.hmgapi-stock -Xint /system/bin "$@"
)
# Root-only stage excludes FAT/ExFAT AppleDouble files, then verifies the
# original author signature. stage-signed.sh is integrity checked by bundle.
[ -f "$USB/../stage-signed.sh" ] && [ ! -L "$USB/../stage-signed.sh" ] || fail STAGE_HELPER_MISSING
. "$USB/../stage-signed.sh"
hmgcli() { cli "$@"; }
stage_signed_payload warmer "$SRC" || fail BAD_SIGNATURE_OR_STAGE
SRC=$STAGED_PAYLOAD
cli hmgapi.engine.Signing verify "$SRC" fa2f9c74-a179-82d0-8c45-f2811df33fbf || fail BAD_SIGNATURE
cli hmgapi.engine.Engine install "$SRC" --uuid fa2f9c74-a179-82d0-8c45-f2811df33fbf || fail INSTALL_FAILED
echo INSTALLED_OFF_REBOOT_REQUIRED > "$OUT/STATUS.txt"
sync
exit 0
