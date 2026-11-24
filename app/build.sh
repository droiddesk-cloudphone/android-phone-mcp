#!/bin/sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK_ROOT=${ANDROID_HOME:-"$HOME/Library/Android/sdk"}
JAVA_HOME=${JAVA_HOME:-"/Applications/Android Studio.app/Contents/jbr/Contents/Home"}
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
BT="$SDK_ROOT/build-tools/36.1.0"
ANDROID="$SDK_ROOT/platforms/android-36.1/android.jar"
OUT="$APP_DIR/build"
mkdir -p "$OUT/classes" "$OUT/gen"
"$BT/aapt" package -f -m -J "$OUT/gen" -M "$APP_DIR/AndroidManifest.xml" -S "$APP_DIR/res" -I "$ANDROID" --min-sdk-version 26 --target-sdk-version 35 -F "$OUT/resources.ap_"
find "$APP_DIR/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -bootclasspath "$ANDROID:$BT/core-lambda-stubs.jar" -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --min-api 26 --lib "$ANDROID" --output "$OUT" @"$OUT/classes.txt"
cp "$OUT/resources.ap_" "$OUT/unsigned.apk"
(cd "$OUT" && "$BT/aapt" add unsigned.apk classes.dex >/dev/null)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$OUT/debug.keystore" ]; then "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$OUT/debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Local Prototype' -keyalg RSA -keysize 2048 -validity 3650 >/dev/null 2>&1; fi
"$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$OUT/connect-debug.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/connect-debug.apk"
echo "Built $OUT/connect-debug.apk (debug-signed; local testing only)."
