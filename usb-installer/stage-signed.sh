# Source from root-only installer; copies only explicitly pinned signed files.
stage_signed_payload() {
 STAGED_PAYLOAD=
 kind=$1; source=$2
 [ -d /data/nemesis ] && [ ! -L /data/nemesis ] || return 1
 [ -d "$source" ] && [ ! -L "$source" ] && [ -d "$source/META-HMGAPI" ] && [ ! -L "$source/META-HMGAPI" ] || return 1
 [ "$(cd "$source" && pwd -P)" = "$source" ] || return 1
 dst=/data/nemesis/bundle-signed-$kind-$$
 [ ! -e "$dst" ] && [ ! -L "$dst" ] || return 1
 (umask 077; mkdir "$dst" && chmod 700 "$dst" && mkdir "$dst/META-HMGAPI") || return 1
 case "$kind" in
  star)
   [ -f "$source/mod.json" ] && [ ! -L "$source/mod.json" ] || return 1
   cp "$source/mod.json" "$dst/mod.json" || return 1
   sum=$(md5 "$dst/mod.json") || return 1; [ "${sum%% *}" = edf80e0dcdfb8de4a158482ca8776768 ] || return 1
   [ -f "$source/META-HMGAPI/manifest.json" ] && [ ! -L "$source/META-HMGAPI/manifest.json" ] || return 1
   cp "$source/META-HMGAPI/manifest.json" "$dst/META-HMGAPI/manifest.json" || return 1
   sum=$(md5 "$dst/META-HMGAPI/manifest.json") || return 1; [ "${sum%% *}" = 0b0e004927946de0a1da514a5177d5d3 ] || return 1
   [ -f "$source/META-HMGAPI/signature" ] && [ ! -L "$source/META-HMGAPI/signature" ] || return 1
   cp "$source/META-HMGAPI/signature" "$dst/META-HMGAPI/signature" || return 1
   sum=$(md5 "$dst/META-HMGAPI/signature") || return 1; [ "${sum%% *}" = c67b5707f405d5295eae4bba8b8f9980 ] || return 1
   expected_author=8843fd55-d673-8045-a574-11e4e09794b0;;
  warmer)
   [ -f "$source/mod.json" ] && [ ! -L "$source/mod.json" ] || return 1
   cp "$source/mod.json" "$dst/mod.json" || return 1
   sum=$(md5 "$dst/mod.json") || return 1; [ "${sum%% *}" = b3e40c2f274a178a7447b8b6ab9572bb ] || return 1
   [ -f "$source/settings.json" ] && [ ! -L "$source/settings.json" ] || return 1
   cp "$source/settings.json" "$dst/settings.json" || return 1
   sum=$(md5 "$dst/settings.json") || return 1; [ "${sum%% *}" = 8bc8a1cbbe0a4965aa44f24bf6580e15 ] || return 1
   [ -f "$source/hooks.jar" ] && [ ! -L "$source/hooks.jar" ] || return 1
   cp "$source/hooks.jar" "$dst/hooks.jar" || return 1
   sum=$(md5 "$dst/hooks.jar") || return 1; [ "${sum%% *}" = e86cae4629950a49cf8203fac52d1b8e ] || return 1
   [ -f "$source/META-HMGAPI/manifest.json" ] && [ ! -L "$source/META-HMGAPI/manifest.json" ] || return 1
   cp "$source/META-HMGAPI/manifest.json" "$dst/META-HMGAPI/manifest.json" || return 1
   sum=$(md5 "$dst/META-HMGAPI/manifest.json") || return 1; [ "${sum%% *}" = dd6c51400b2981a9934bca3c54e51577 ] || return 1
   [ -f "$source/META-HMGAPI/signature" ] && [ ! -L "$source/META-HMGAPI/signature" ] || return 1
   cp "$source/META-HMGAPI/signature" "$dst/META-HMGAPI/signature" || return 1
   sum=$(md5 "$dst/META-HMGAPI/signature") || return 1; [ "${sum%% *}" = 59586426102d3414bdbb7d28ad8af313 ] || return 1
   expected_author=fa2f9c74-a179-82d0-8c45-f2811df33fbf;;
  *) return 1;;
 esac
 hmgcli hmgapi.engine.Signing verify "$dst" "$expected_author" || return 1
 STAGED_PAYLOAD=$dst
 return 0
}
