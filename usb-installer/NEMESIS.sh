#!/system/bin/sh
export PATH=/system/bin:/system/xbin:/data/nemesis/bin
umask 077
U=${0%/*}; [ "$U" != "$0" ] || U=$PWD
U=$(cd "$U" && pwd -P) || exit 2
case "$U/" in /storage/*|/mnt/*) ;; *) exit 2;; esac
OUT="$U/WARMER_INSTALL_$(date +%Y%m%d_%H%M%S)_$$"
mkdir "$OUT" || exit 2
exec > "$OUT/INSTALL.log" 2>&1
finish(){ echo "$1" > "$OUT/STATUS.txt"; echo "$1"; sync; exit "$2"; }
case "$(id)" in 'uid=0('*|'uid=0 '*|'uid=0') ;; *) finish ROOT_REQUIRED 1;; esac
[ "$(getprop ro.build.product)" = NEEV.KOR.0000.V039.001.260902 ] || finish UNSUPPORTED_PRODUCT 1
[ "$(getprop ro.board.platform)" = tcc803x ] || finish UNSUPPORTED_PLATFORM 1
[ -x /system/xbin/nemesis ] && [ -x /system/bin/app_process.hmgapi-stock ] && [ -f /system/framework/hmgapi-hook.jar ] || finish NEMESIS_OR_HMGAPI_MISSING 1
[ -f "$U/stage-signed.sh" ] && [ ! -L "$U/stage-signed.sh" ] || finish FILE_MISSING 1
sum=$(md5 "$U/stage-signed.sh") || finish FILE_INVALID 1
[ "${sum%% *}" = 560756dd5def2c7a12044fe1446c8756 ] || finish FILE_CHANGED 1
[ -f "$U/update-description.sh" ] && [ ! -L "$U/update-description.sh" ] || finish FILE_MISSING 1
sum=$(md5 "$U/update-description.sh") || finish FILE_INVALID 1
[ "${sum%% *}" = 34d4b9e490eb998eda4cc90ac1f6dd31 ] || finish FILE_CHANGED 1
[ -f "$U/WARMER/NEMESIS.sh" ] && [ ! -L "$U/WARMER/NEMESIS.sh" ] || finish FILE_MISSING 1
sum=$(md5 "$U/WARMER/NEMESIS.sh") || finish FILE_INVALID 1
[ "${sum%% *}" = 9d7f36824ba601dd85d6a8eb84a041bd ] || finish FILE_CHANGED 1
/system/xbin/nemesis pkg-list > "$OUT/package-list.txt" 2>&1 || finish PACKAGE_LIST_FAILED 1
v=; count=0
while IFS='|' read rid name enabled kind version description; do
 [ "$rid" = hmgapi.neev-warmer-mapless-research ] || continue
 count=$((count+1)); v=$version
done < "$OUT/package-list.txt"
[ "$count" -le 1 ] || finish DUPLICATE_PACKAGE 1
if [ "$count" = 1 ]; then
 case "$v" in ''|.*|*.|*..*|*[!0-9.]*) finish UNKNOWN_VERSION 1;; esac
 # Compare arbitrary numeric version components without coercing text.
 left=$v; right=0.0.23; relation=equal
 while [ -n "$left" ] || [ -n "$right" ]; do
  a=${left%%.*}; b=${right%%.*}
  if [ "$a" = "$left" ]; then left=; else left=${left#*.}; fi
  if [ "$b" = "$right" ]; then right=; else right=${right#*.}; fi
  [ -n "$a" ] || a=0; [ -n "$b" ] || b=0
  while [ "${#a}" -gt 1 ] && [ "${a#0}" != "$a" ]; do a=${a#0}; done
  while [ "${#b}" -gt 1 ] && [ "${b#0}" != "$b" ]; do b=${b#0}; done
  [ "${#a}" -le 8 ] && [ "${#b}" -le 8 ] || finish UNKNOWN_VERSION 1
  if [ "$a" -gt "$b" ]; then relation=newer; break; fi
  if [ "$a" -lt "$b" ]; then relation=older; break; fi
 done
 if [ "$relation" = newer ]; then finish KEPT_NEWER 0; fi
 if [ "$relation" = equal ]; then
  /system/bin/sh "$U/update-description.sh" neev-warmer-mapless-research 0.0.23 || echo DESCRIPTION_NOT_UPDATED
  finish ALREADY_INSTALLED 0
 fi
fi
# Same signed payload and installer as the user's tested all-in-one bundle.
/system/bin/sh "$U/WARMER/NEMESIS.sh" || finish INSTALL_FAILED 1
/system/xbin/nemesis pkg-list > "$OUT/package-list-after.txt" 2>&1 || finish PACKAGE_LIST_FAILED 1
found=0
while IFS='|' read rid name enabled kind version description; do
 [ "$rid" != hmgapi.neev-warmer-mapless-research ] || [ "$version" != 0.0.23 ] || found=$((found+1))
done < "$OUT/package-list-after.txt"
[ "$found" = 1 ] || finish REGISTRATION_NOT_FOUND 1
/system/bin/sh "$U/update-description.sh" neev-warmer-mapless-research 0.0.23 || echo DESCRIPTION_NOT_UPDATED
finish INSTALLED_REBOOT_REQUIRED 0
