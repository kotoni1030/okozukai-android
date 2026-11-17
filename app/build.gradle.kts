plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
}
android {
    namespace = "jp.local.okozukai"
    compileSdk = 37
    defaultConfig {
        // 日常利用版の識別子・署名は配布前に確定する。
        applicationId = "jp.local.okozukai.dev"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "jp.local.okozukai.LedgerTestRunner"
    }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    sourceSets.getByName("test").resources.directories.add("$rootDir/examples")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.savedstate)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    androidTestImplementation(libs.room.testing)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.android.junit)
    androidTestImplementation(libs.android.runner)
    // Composeの推移的な旧版ではAPI 37のInputManagerに対応できないため固定する。
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
