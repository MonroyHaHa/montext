plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

android {
    namespace = "com.monroy.montext"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.monroy.montext"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true // 启用 View Binding
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Lifecycle & ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")

    // Navigation Component (用于底部导航栏和 Fragment 导航)
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    // 使用 ksp 替代 kapt
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1") // Kotlin Coroutines 支持

    // Smack 4.4.6 (XMPP 客户端库)
    // Smack 4.4.6 (XMPP 客户端库)
    implementation("org.igniterealtime.smack:smack-android:4.4.6") {
        // 尝试在这里排除 xpp3_min
        exclude(group = "xpp3", module = "xpp3_min")
    }
    implementation("org.igniterealtime.smack:smack-tcp:4.4.6")
    implementation("org.igniterealtime.smack:smack-im:4.4.6")
    implementation("org.igniterealtime.smack:smack-extensions:4.4.6")
    implementation("org.igniterealtime.smack:smack-android-extensions:4.4.6"){
        // 也可以在这里排除 xpp3_min，如果上面的排除没有解决问题
        exclude(group = "xpp3", module = "xpp3_min")
    }
    // 可能还需要其他 Smack 扩展，根据后续功能添加，例如文件传输、音视频等
    // implementation("org.igniterealtime.smack:smack-omemo:4.4.6") // OMEMO 加密
    // implementation("org.igniterealtime.smack:smack-file-transfer:4.4.6") // 文件传输

    // Logging (可选，但推荐)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0") // 与 lifecycle-viewmodel-ktx 版本一致
    // --- 根据未来需求可能需要的依赖 ---

    // 图片加载库 (例如 Coil，如果您更喜欢 Glide 也可以)
    // Coil - 专为 Kotlin Coroutines 和 Compose 设计
    implementation("io.coil-kt:coil-compose:2.6.0") // 使用最新稳定版

    // Kotlin 序列化 (如果需要处理 JSON/其他数据格式)
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // 使用最新稳定版

    // Accompanist Permissions (Compose 权限请求)
    // implementation("com.google.accompanist:accompanist-permissions:0.34.0") // 使用最新稳定版

    // Smack 额外的扩展 (根据您的具体 XMPP 需求解注释和添加)
    // 如果要支持文件传输
    // implementation("org.igniterealtime.smack:smack-file-transfer:4.4.6") {
    //     exclude(group = "xpp3", module = "xpp3_min")
    // }
    // 如果要支持 OMEMO 加密
    // implementation("org.igniterealtime.smack:smack-omemo:4.4.6") {
    //     exclude(group = "xpp3", module = "xpp3_min")
    // }
    // 如果要支持 BOSH
    // implementation("org.igniterealtime.smack:smack-bosh:4.4.6") {
    //     exclude(group = "xpp3", module = "xpp3_min")
    // }

    // 注意：您已经有了 platform(libs.androidx.compose.bom)，
    // 这意味着大部分 Compose 库的版本都由 BOM 管理，
    // 所以您不需要为单独的 Compose 库指定版本号，例如 'androidx.compose.ui:ui:1.x.x'
    // libs.androidx.activity.compose 也是由 BOM 管理的。

    // 检查这个：您已经有了 libs.androidx.lifecycle.runtime.ktx，但又单独添加了 "androidx.lifecycle:lifecycle-runtime-ktx:2.8.0"。
    // 通常 libs 里面的会优先，建议移除重复的直接依赖声明，保持一致性。
    // 如果 libs.androidx.lifecycle.runtime.ktx 对应的是 2.8.0，那就没问题。
    // 但是 libs.androidx.activity 和 libs.androidx.activity.compose 是重复的，
    // activity-compose 应该会包含 activity 的功能，可以移除单独的 libs.androidx.activity。

    // 建议清理：
    // 移除：implementation("androidx.core:core-ktx:1.13.1")
    // 移除：implementation(libs.androidx.core.ktx) // 保留其中一个最新版即可
    // 移除：implementation(libs.androidx.activity) // activity-compose 已经包含了
}