param(
    [string]$Sdk = 'D:/environments/Android/Sdk',
    [string]$BuildTools = '36.0.0',
    [string]$Platform = 'android-36',
    [string]$NdkVersion = '30.0.16248370',
    [string]$GradleHome = 'D:/environments/Android/gradle-9.7.1'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
Push-Location $PSScriptRoot
try {
    $tools = Join-Path $Sdk "build-tools/$BuildTools"
    $android = Join-Path $Sdk "platforms/$Platform/android.jar"
    $d8 = Join-Path $Sdk 'cmdline-tools/latest/lib/r8-classpath.jar'
    if (!(Test-Path -LiteralPath $d8)) {
        throw 'Install Android SDK Command-line Tools (latest) for Kotlin-compatible D8.'
    }
    $compilerLib = Join-Path $GradleHome 'lib'
    $stdlib = Join-Path $compilerLib 'kotlin-stdlib-2.4.0.jar'
    if (!(Test-Path -LiteralPath $stdlib)) {
        throw 'Kotlin 2.4.0 was not found. Set -GradleHome to a Gradle 9.7.1 installation.'
    }
    New-Item -ItemType Directory build/dex,build/tmp -Force | Out-Null
    $env:TEMP = (Resolve-Path -LiteralPath build/tmp).Path
    $env:TMP = $env:TEMP
    $sources = (Get-ChildItem -LiteralPath src -Filter '*.kt' -File).FullName
    java -cp "$compilerLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 -classpath "$android;$stdlib" -d build/app.jar $sources
    java -cp $d8 com.android.tools.r8.D8 --lib $android --min-api 29 --output build/dex build/app.jar $stdlib
    $clang = Join-Path $Sdk "ndk/$NdkVersion/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
    $targets = [ordered]@{
        'arm64-v8a' = 'aarch64-linux-android29'
        'x86_64' = 'x86_64-linux-android29'
    }
    foreach ($abi in $targets.Keys) {
        $lib = "build/lib/$abi"
        New-Item -ItemType Directory $lib -Force | Out-Null
        & $clang "--target=$($targets[$abi])" -shared -fPIC -O2 -DNO_DEBUG '-Wl,-soname,libzdtun.so' '-Wl,-z,max-page-size=16384' third_party/zdtun/zdtun.c third_party/zdtun/utils.c -o "$lib/libzdtun.so"
        & $clang "--target=$($targets[$abi])" -shared -fPIC -O2 -DNO_DEBUG -Ithird_party/zdtun '-Wl,-z,max-page-size=16384' '-Wl,--no-undefined' src/capture.c "-L$lib" -lzdtun -o "$lib/libcapture.so"
    }
    & "$tools/aapt2.exe" link -I $android --manifest src/AndroidManifest.xml -o build/base.apk
    @'
from pathlib import Path
import sys
import zipfile

with zipfile.ZipFile("build/base.apk", "a", zipfile.ZIP_DEFLATED) as apk:
    apk.write("build/dex/classes.dex", "classes.dex")
    for abi in sys.argv[1:]:
        for name in ("libzdtun.so", "libcapture.so"):
            path = f"lib/{abi}/{name}"
            apk.write(f"build/{path}", path)
    for name in ("COPYING", "GPL-3.0.txt", "README.md"):
        apk.write(Path("third_party/zdtun") / name, f"assets/zdtun/{name}")
'@ | python - @($targets.Keys)
    & "$tools/zipalign.exe" -f 4 build/base.apk build/aligned.apk
    if (!(Test-Path build/local.p12)) {
        keytool -genkeypair -keystore build/local.p12 -storepass localbuild -alias blocker -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Local SystemHelper Blocker'
    }
    & "$tools/apksigner.bat" sign --ks build/local.p12 --ks-pass pass:localbuild --out build/SystemHelperBlocker.apk build/aligned.apk
    & "$tools/apksigner.bat" verify build/SystemHelperBlocker.apk
    Write-Output (Join-Path $PSScriptRoot 'build/SystemHelperBlocker.apk')
} finally {
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
    Pop-Location
}
