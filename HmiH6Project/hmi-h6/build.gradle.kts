plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * H6 HMI 资源：分辨率（构建期二选一）+ 主题（构建期二选一）+ 黑白夜（运行时自动）。
 *
 * 目录结构：
 *
 *   src/main/res_1280x720/{layout,values,drawable,mipmap}/    720 屏（1280x720）专属资源
 *   src/main/res_1920x1080/{layout,values,drawable,mipmap}/   1080 屏（1920x1080）专属资源
 *   src/main/theme_blue/{values,values-night}/                蓝色主题色板（白天 / 夜间）
 *   src/main/theme_red/{values,values-night}/                 红色主题色板（白天 / 夜间）
 *   src/main/res/navigation/                                  与分辨率、主题都无关的公共资源
 *
 * 三个维度怎么分工：
 *   1. 分辨率：内层目录不带 -1280x720 限定符，同一时刻只挂一个分辨率目录，
 *      内层资源就是「默认资源」；换分辨率要重新 publish。
 *        ./gradlew publish -Ph6.resolution=1920x1080      # 默认 1280x720
 *   2. 主题：Android 没有「主题限定符」，多套色板只能构建期选一套，
 *      所以按 theme_<主题> 分目录；换主题同样要重新 publish。
 *        ./gradlew publish -Ph6.theme=red                 # 默认 blue
 *      颜色按用途命名（h6_home_bg / h6_home_card_bg / ...），布局与 drawable 只引用颜色名，
 *      换主题不动布局。
 *   3. 黑白夜：走标准 -night 限定符（每个 theme_* 下的 values-night/colors.xml），
 *      运行时由系统 uiMode 自动切换，零代码零判断（宿主主题必须是 DayNight 系）。
 */
val h6AvailableResolutions: List<String> =
    file("src/main")
        .listFiles { f -> f.isDirectory && f.name.startsWith("res_") }
        .orEmpty()
        .map { it.name.removePrefix("res_") }
        .sorted()

val h6Resolution: String =
    providers.gradleProperty("h6.resolution").orNull
        ?: "1280x720".takeIf { it in h6AvailableResolutions }
        ?: h6AvailableResolutions.firstOrNull()
        ?: throw GradleException("src/main 下没有 res_<分辨率> 目录")

// 配置期就报错，别等到资源合并时才出现难查的提示
check(h6Resolution in h6AvailableResolutions) {
    "h6.resolution=$h6Resolution 不可用，可选：${h6AvailableResolutions.joinToString(" / ")}"
}

val h6AvailableThemes: List<String> =
    file("src/main")
        .listFiles { f -> f.isDirectory && f.name.startsWith("theme_") }
        .orEmpty()
        .map { it.name.removePrefix("theme_") }
        .sorted()

val h6Theme: String =
    providers.gradleProperty("h6.theme").orNull
        ?: "blue".takeIf { it in h6AvailableThemes }
        ?: h6AvailableThemes.firstOrNull()
        ?: throw GradleException("src/main 下没有 theme_<主题> 目录")

check(h6Theme in h6AvailableThemes) {
    "h6.theme=$h6Theme 不可用，可选：${h6AvailableThemes.joinToString(" / ")}"
}

val h6ResSourceDir = "src/main/res_$h6Resolution"
val h6ThemeSourceDir = "src/main/theme_$h6Theme"

android {
    namespace = "com.sky.automap.hmi.h6"

    publishing {
        singleVariant("release")
    }

    sourceSets.getByName("main") {
        // src/main/res 是默认值，显式写出来，避免 res.srcDirs(...) 覆盖时把它丢掉
        res.srcDirs(listOf("src/main/res", h6ResSourceDir, h6ThemeSourceDir))
    }
}

// 跨工程依赖版本：唯一来源是 CarMapProject/versions.properties，
// 由 ./sync-versions.py 同步到 gradle.properties 的 automap.version.*（按 artifact 独立，支持单模块升版）
fun skyVersionOf(artifact: String): String =
    providers.gradleProperty("automap.version.$artifact").orNull
        ?: throw GradleException(
            "gradle.properties 缺少 automap.version.$artifact，请先执行 CarMapProject/sync-versions.py"
        )

dependencies {
    // HMI 契约（HmiH6Entry 实现 HmiEntry，属于公开签名，必须 api）
    api("com.sky.automap:hmi-api:${skyVersionOf("hmi-api")}")

    // 依赖基建层 AAR
    api("com.sky.automap:infrastructure:${skyVersionOf("infrastructure")}")

    // 依赖业务组件层 AAR（SkyBusinessComponent，一个 AAR 内含 poi / navigation / route）
    implementation("com.sky.automap:business:${skyVersionOf("business")}")
}
