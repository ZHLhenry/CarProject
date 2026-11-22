plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sky.demo.carclient"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.sky.demo.carclient"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // demo 不需要 BuildConfig / ViewBinding，保持最小
        buildConfig = false
    }

    buildTypes {
        release {
            // 演示工程不混淆：先把「能不能绑上」看清楚，混淆问题不该混进来
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // 全部依赖就这一行：契约 AAR 自带了 IRemoteCarService / IRemoteCarCallback /
    // CarIdentity / CarApi，调用方不需要对方的任何源码或 .aidl 文件。
    implementation(libs.car.api)
}
