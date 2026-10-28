import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.manytwo.ncplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.manytwo.ncplayer"
        minSdk = 29
        targetSdk = 36
        versionCode = 24
        versionName = "1.23"
        buildConfigField("String", "BUILD_TIME",
            "\"" + SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()) + "\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    applicationVariants.all {
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "m21-nextcloud-player.apk"
        }
    }
}

// The user guide (USERGUIDE.md at the repo root) is bundled into the APK so Help works offline.
val copyGuide by tasks.registering(Copy::class) {
    from(rootProject.file("USERGUIDE.md"))
    into(layout.buildDirectory.dir("generated/guide"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/guide"))
tasks.named("preBuild") { dependsOn(copyGuide) }

dependencies {
    implementation("org.commonmark:commonmark:0.22.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")
    implementation("org.commonmark:commonmark-ext-heading-anchor:0.22.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
