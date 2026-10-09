#!/usr/bin/env bash
# Direct build fallback used for the delivered APK. Requires Java 17 runtime.
set -euo pipefail
: "${SET_ANDROID_SDK:?Path to Android SDK with platform 35 and build tools 35.0.0}"
: "${SET_COMPILER_LIB:?Path to Gradle 8.11.1 lib directory (Kotlin compiler 2.0.20)}"
: "${SET_ECJ_JAR:?Path to Eclipse Java Compiler 3.38.0 jar}"
: "${SET_JUNIT_JAR:?Path to JUnit 4.13.2 jar}"
: "${SET_HAMCREST_JAR:?Path to Hamcrest Core 1.3 jar}"
cd "$(dirname "$0")/.."
set_build_dir="$PWD/build/direct"
set_tools="$SET_ANDROID_SDK/build-tools/35.0.0"
set_android_jar="$SET_ANDROID_SDK/platforms/android-35/android.jar"
set_stdlib="$SET_COMPILER_LIB/kotlin-stdlib-2.0.20.jar"
mkdir -p "$set_build_dir"/{compiled,java,classes,test,dex}
export SET_DIRECT_BUILD_DIR="$set_build_dir"
python3 - <<'PY'
import os
from pathlib import Path
p=Path('app/src/main/AndroidManifest.xml').read_text().replace('<manifest ', '<manifest package="dev.setpocket" ')
Path(os.environ['SET_DIRECT_BUILD_DIR'],'AndroidManifest.xml').write_text(p)
PY
"$set_tools/aapt2" compile --dir app/src/main/res -o "$set_build_dir/compiled/"
"$set_tools/aapt2" link -o "$set_build_dir/base.apk" --manifest "$set_build_dir/AndroidManifest.xml" -I "$set_android_jar" --java "$set_build_dir/java" --min-sdk-version 26 --target-sdk-version 35 --version-code 6 --version-name 0.5.0 "$set_build_dir"/compiled/*.flat
java -jar "$SET_ECJ_JAR" -17 -d "$set_build_dir/classes" "$set_build_dir/java/dev/setpocket/R.java"
java -cp "$SET_COMPILER_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$set_android_jar:$set_stdlib:$set_build_dir/classes" -d "$set_build_dir/classes" app/src/main/java/dev/setpocket/*.kt
java -cp "$SET_COMPILER_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$set_stdlib:$SET_JUNIT_JAR:$set_build_dir/classes" -d "$set_build_dir/test" app/src/test/java/dev/setpocket/RulesTest.kt
java -cp "$set_stdlib:$SET_JUNIT_JAR:$SET_HAMCREST_JAR:$set_build_dir/classes:$set_build_dir/test" org.junit.runner.JUnitCore dev.setpocket.RulesTest
python3 - <<'PY'
import os,zipfile
from pathlib import Path
b=Path(os.environ['SET_DIRECT_BUILD_DIR'])
with zipfile.ZipFile(b/'classes.jar','w') as z:
 for p in (b/'classes').rglob('*.class'): z.write(p,p.relative_to(b/'classes'))
PY
"$set_tools/d8" --release --min-api 26 --lib "$set_android_jar" --output "$set_build_dir/dex" "$set_build_dir/classes.jar" "$set_stdlib" "$SET_COMPILER_LIB/annotations-24.0.1.jar"
python3 - <<'PY'
import os,shutil,zipfile
from pathlib import Path
b=Path(os.environ['SET_DIRECT_BUILD_DIR'])
shutil.copy(b/'base.apk',b/'unsigned.apk')
with zipfile.ZipFile(b/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for p in (b/'dex').glob('*.dex'): z.write(p,p.name)
PY
"$set_tools/zipalign" -f -p 4 "$set_build_dir/unsigned.apk" "$set_build_dir/aligned.apk"
set_signing_key="$PWD/dev-signing/debug.keystore"
if [[ ! -f "$set_signing_key" ]]; then
    set_signing_key="$set_build_dir/debug.keystore"
    if [[ ! -f "$set_signing_key" ]]; then
        keytool -genkeypair -keystore "$set_signing_key" -storepass android -keypass android \
            -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' \
            -keyalg RSA -keysize 2048 -validity 10000
    fi
fi
"$set_tools/apksigner" sign --ks "$set_signing_key" --ks-pass pass:android --key-pass pass:android --out "$set_build_dir/SET-Pocket-0.5.0.apk" "$set_build_dir/aligned.apk"
"$set_tools/apksigner" verify --verbose "$set_build_dir/SET-Pocket-0.5.0.apk"
printf 'APK: %s\n' "$set_build_dir/SET-Pocket-0.5.0.apk"
