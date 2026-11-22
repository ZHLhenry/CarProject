plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 契约 + 门面：`com.sky.automap.map.api`（接口 / 数据模型，零可变状态）
 * 与 `com.sky.automap.map.factory`（MapManager + internal 注册表）同处一个 AAR。
 *
 * namespace 只决定 R / BuildConfig，与 Kotlin 包无关，因此两个包可以共用一个模块；
 * 业务组件与壳工程只会依赖这一个坐标，实现仍由壳工程按车型注入。
 */
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
