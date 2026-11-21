plugins {
    alias(libs.plugins.sky.android.library.common)
}

android {
    namespace = "com.sky.automap.hmi.api"

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // HMI 契约层：不依赖任何 UI / 业务实现，壳工程与各车型 HMI 工程共同依赖
    api(libs.androidx.core.ktx)
}
