plugins {
    alias(libs.plugins.sky.android.library.common)
}

// 跨工程 AAR 版本：唯一来源是本工程根目录的 versions.properties（同 app/build.gradle.kts）
fun skyVersionOf(artifact: String): String {
    val key = "$artifact="
    return rootProject.file("versions.properties")
        .takeIf { it.isFile }
        ?.readLines()
        ?.firstOrNull { it.trim().startsWith(key) }
        ?.substringAfter("=")?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("CarMapProject/versions.properties 里没有 $artifact 这一行")
}

android {
    namespace = "com.sky.automap.car.service"
}

dependencies {
    // 对外 AIDL 契约（IRemoteCarService / IRemoteCarCallback / CarIdentity）
    // implementation 而不是 api：:app 只需要 RemoteCarBridge 这类注入入口，
    // 不需要在编译期看到 Stub —— 契约只对「写 Binder 代码的这一层」可见。
    implementation("com.sky.automap:car-api:${skyVersionOf("car-api")}")

    // 地图能力层（MapManager 门面 + IMapProvider 接口）。
    // 只依赖 map-api（契约 + 门面），不依赖任何 map-impl-* / map-sdk-*：
    // 具体实现由 :app 按渠道注入，本模块只管转调。
    implementation("com.sky.automap:map-api:${skyVersionOf("map-api")}")
}
