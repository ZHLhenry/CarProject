plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 业务组件层：**一个模块、一个 AAR**（`com.sky.automap:business`）。
 *
 * 里面按业务分包：
 *   com.sky.automap.component.poi        → POI 检索与打点
 *   com.sky.automap.component.navigation → 导航引导
 *   com.sky.automap.component.route      → 路线 / 途经点
 *
 * 为什么不再拆成 poi / navigation / route 三个模块：
 *   1. 三者的依赖完全相同（infrastructure + map-api），拆开只是多两次 AAR 装配；
 *   2. 三者天然要一起用（HMI 的导航图同时 include 三张子图），拆开后下游必须一次性引三个坐标，
 *      少引一个就是运行期 "destination not found"；
 *   3. 业务组件是「一起演进」的，版本号没必要各自独立。
 *
 * 保留的是**包边界**而不是构建边界：跨包引用仍然显式（谁依赖谁一眼能看出来），
 * 但发布、版本、依赖声明收敛成一份。
 *
 * 依赖红线不变：只许依赖 infrastructure / map-api（契约 + 门面），严禁依赖地图厂商实现与 SDK。
 */
android {
    // 命名空间取三个包的公共父级，因此 R 只有一份
    namespace = "com.sky.automap.component"

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
    // 依赖基建层 AAR（Fragment/ViewModel 基类出现在公开签名中，故为 api）
    api("com.sky.automap:infrastructure:${skyVersionOf("infrastructure")}")
    // 依赖地图 AAR：`map.api` 包（接口与数据模型，不含任何厂商 SDK，公开签名里出现，故为 api）
    // + `map.factory` 包（MapManager，组件取用地图能力的唯一入口，实现由壳工程注入）
    api("com.sky.automap:map-api:${skyVersionOf("map-api")}")
}
