plugins {
    alias(libs.plugins.sky.android.library.common)
}

// 对外发布 release 变体的软件组件（AAR + POM）
android {
    namespace = "com.sky.automap.map.api"

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // 接口层/标准 SDK：只允许依赖 AndroidX 基础注解，不引入任何地图厂商 SDK
    api(libs.androidx.core.ktx)
}
