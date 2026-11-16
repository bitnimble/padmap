#!/bin/bash
# Builds build/padmap.apk and runs the JVM tests. Needs JDK 17+ and the Android SDK (build-tools 36, platform 36):
# ANDROID_HOME, default ~/android/sdk. Signs with ~/.android/debug.keystore, creating it if missing.
set -euo pipefail
R="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/android/sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
B="$R/build"
W="$B/work"
KEY="$HOME/.android/debug.keystore"

rm -rf "$W"
mkdir -p "$W/classes" "$W/dex" "$W/test"
find "$R/src" -name '*.java' >"$W/sources"
javac --release 17 -Xlint:all,-options,-serial -Werror -classpath "$JAR" -d "$W/classes" @"$W/sources"

javac --release 17 -d "$W/test" "$R"/src/app/padmap/layout/{Control,Layout,PadState,Mapper}.java \
    "$R"/src/app/padmap/daemon/Getevent.java "$R"/test/*.java
for test in MapperTest app.padmap.daemon.GeteventTest; do
    java -cp "$W/test" "$test"
done

mapfile -t classes < <(find "$W/classes" -name '*.class')
"$BT/d8" --min-api 30 --lib "$JAR" --output "$W/dex" "${classes[@]}"
"$BT/aapt2" link -o "$W/unsigned.apk" -I "$JAR" --manifest "$R/AndroidManifest.xml" -A "$R/assets"
(cd "$W/dex" && zip -q "$W/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$W/unsigned.apk" "$W/aligned.apk"
if [[ ! -f "$KEY" ]]; then
    mkdir -p "$(dirname "$KEY")"
    keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi
"$BT/apksigner" sign --ks "$KEY" --ks-pass pass:android --out "$B/padmap.apk" "$W/aligned.apk"
rm -f "$B/padmap.apk.idsig"
echo "$B/padmap.apk"
