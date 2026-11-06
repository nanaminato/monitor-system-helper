param(
    [string]$Sdk = 'D:/environments/Android/Sdk',
    [string]$BuildTools = '36.0.0',
    [string]$Platform = 'android-36',
    [string]$NdkVersion = '30.0.16248370'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
Push-Location $PSScriptRoot
try {
    $tools = Join-Path $Sdk "build-tools/$BuildTools"
    $android = Join-Path $Sdk "platforms/$Platform/android.jar"
    New-Item -ItemType Directory build/classes,build/dex -Force | Out-Null
    javac --release 8 -encoding UTF-8 -cp $android -d build/classes src/MainActivity.java src/BlockService.java src/MonitorActivity.java src/CaptureService.java
    & "$tools/d8.bat" --lib $android --min-api 29 --output build/dex build/classes/local/helperblock/MainActivity.class build/classes/local/helperblock/BlockService.class build/classes/local/helperblock/MonitorActivity.class build/classes/local/helperblock/CaptureService.class
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
    Pop-Location
}
