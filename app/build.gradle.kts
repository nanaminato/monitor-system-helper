plugins {
    id("com.android.application") version "9.4.1"
}

val localTestKey = rootProject.file("build/local.p12")

android {
    namespace = "local.helperblock"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "local.helperblock"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    sourceSets.named("main") {
        manifest.srcFile("../src/AndroidManifest.xml")
        kotlin.directories.add("../src")
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    if (localTestKey.isFile) {
        signingConfigs.named("debug") {
            storeFile = localTestKey
            storeType = "PKCS12"
            storePassword = "localbuild"
            keyAlias = "blocker"
            keyPassword = "localbuild"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

val noticesDir = layout.buildDirectory.dir("generated/zdtunAssets").get().asFile
val zdtunNotices = tasks.register<Copy>("copyZdtunNotices") {
    from(rootProject.file("third_party/zdtun")) {
        include("COPYING", "GPL-3.0.txt", "README.md")
    }
    into(noticesDir.resolve("zdtun"))
}

android.sourceSets.named("main") {
    assets.directories.add(noticesDir.absolutePath)
}

tasks.named("preBuild") {
    dependsOn(zdtunNotices)
}
