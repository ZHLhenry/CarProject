plugins {
    alias(libs.plugins.sky.android.library.common)
}

android {
    namespace = "com.sky.automap.hmi.h9"

    publishing {
        singleVariant("release")
    }
}

// 跨工程依赖版本：唯一来源是 CarMapProject/versions.properties（全仓库只此一份），这里直接读它。
fun skyVersionOf(artifact: String): String {
    // 版本表是纯文本，直接按行读（不用 java.util.Properties：kts 里 `java` 会被
    // JavaPluginExtension 遮蔽）；注释与空行跳过
    val key = "$artifact="
    return rootProject.file("../CarMapProject/versions.properties")
        .takeIf { it.isFile }
        ?.readLines()
        ?.firstOrNull { it.trim().startsWith(key) }
        ?.substringAfter("=")?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("CarMapProject/versions.properties 里没有 $artifact 这一行")
}

dependencies {
    // HMI 契约（HmiH9Entry 实现 HmiEntry，属于公开签名，必须 api）
    api("com.sky.automap:hmi-api:${skyVersionOf("hmi-api")}")

    // 依赖基建层 AAR
    api("com.sky.automap:infrastructure:${skyVersionOf("infrastructure")}")

    // 依赖业务组件层 AAR（SkyBusinessComponent，一个 AAR 内含 poi / navigation / route）
    implementation("com.sky.automap:business:${skyVersionOf("business")}")
}
