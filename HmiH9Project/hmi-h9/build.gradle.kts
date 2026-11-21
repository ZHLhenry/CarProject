plugins {
    alias(libs.plugins.sky.android.library.common)
}

android {
    namespace = "com.sky.automap.hmi.h9"

    publishing {
        singleVariant("release")
    }
}

// 跨工程依赖版本：唯一来源是 CarMapProject/versions.properties，
// 由 ./versions.py sync 同步到 gradle.properties 的 automap.version.*（按 artifact 独立，支持单模块升版）
fun skyVersionOf(artifact: String): String =
    providers.gradleProperty("automap.version.$artifact").orNull
        ?: throw GradleException(
            "gradle.properties 缺少 automap.version.$artifact，请先执行 CarMapProject/versions.py sync"
        )

dependencies {
    // HMI 契约（HmiH9Entry 实现 HmiEntry，属于公开签名，必须 api）
    api("com.sky.automap:hmi-api:${skyVersionOf("hmi-api")}")

    // 依赖基建层 AAR
    api("com.sky.automap:infrastructure:${skyVersionOf("infrastructure")}")

    // 依赖业务组件层 AAR（SkyBusinessComponent，一个 AAR 内含 poi / navigation / route）
    implementation("com.sky.automap:business:${skyVersionOf("business")}")
}
