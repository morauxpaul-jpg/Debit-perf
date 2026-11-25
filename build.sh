#!/usr/bin/env bash
# Compile Débit Perf (Wear OS) sans Gradle. Usage : ./build.sh <dossier SDK Android>
# Il faut build-tools 35.0.1 ou plus récent : le d8 de build-tools 34.0.0 plante sur les classes anonymes.
# Le script prend automatiquement la version la plus récente installée.
set -euo pipefail
SDK="${1:-${ANDROID_HOME:-}}"
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
PLAT=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/res build/classes build/dex build/assets
# Interface téléphone : la page web/index.html est embarquée dans l'APK.
cp web/index.html build/assets/index.html
"$BT/aapt2" compile --dir res -o build/res/
"$BT/aapt2" link -o build/base.apk -I "$PLAT" --manifest AndroidManifest.xml -A build/assets \
  --min-sdk-version 30 --target-sdk-version 34 --version-code 3 --version-name 1.2 build/res/*.flat
javac -encoding UTF-8 --release 11 -cp "$PLAT" -d build/classes $(find src -name '*.java')
"$BT/d8" --min-api 30 --lib "$PLAT" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks debitperf.keystore --ks-pass pass:debitperf --key-pass pass:debitperf \
  --out DebitPerf.apk build/aligned.apk
"$BT/apksigner" verify --print-certs DebitPerf.apk
ls -la DebitPerf.apk
