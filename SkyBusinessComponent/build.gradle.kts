import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// ============================================================================
// SkyBusinessComponent —— 业务组件层（独立工程）
//
// 产物（发布到 ../CarMapProject/maven-repo）：**一个 AAR**
//   com.sky.automap:business → POI 检索 / 导航引导 / 路线规划
//
// 内部按业务分包（com.sky.automap.component.{poi, navigation, route}），
// 但不再拆成三个模块：三者依赖完全相同、天然一起用（HMI 导航图同时 include 三张子图），
// 拆开只会让下游多引坐标、多出「少引一个就运行期崩」的机会。
//
// 每个业务包自带 Fragment + 子导航图，由 HMI 工程 include 组合；
// 只允许依赖 infrastructure / map-api（契约 + 门面），严禁依赖具体地图实现。
// 构建：./gradlew publish
// ============================================================================

// SkyBuild 全局共享配置（供本工程所有模块继承）
extra["skyBuild.appName"] = "SkyBusinessComponent"
extra["skyBuild.applicationId"] = "com.sky.automap"
// 版本号：库工程不声明 versionCode / versionName。
// AGP 的 LibraryDefaultConfig 没有这两个属性，SkyBuildLogic 也只把它们写给 application 模块，
// 写在这里既进不了 AAR，也不影响 APK，只会让人误以为「改这里能改版本」。
//   · APK 的 versionName / versionCode → 只在 CarMapProject/build.gradle.kts 声明
//   · AAR 的版本（Maven 坐标里的 1.0.0） → CarMapProject 下的 versions.properties（按 artifact 独立）
extra["skyBuild.compileSdk"] = 37
extra["skyBuild.minSdk"] = 24
extra["skyBuild.targetSdk"] = 35
extra["skyBuild.enableViewBinding"] = true
extra["skyBuild.enableDataBinding"] = false
extra["skyBuild.enableBuildConfig"] = true
extra["skyBuild.enableCompose"] = false

// ============================ AAR 发布约定 ====================================
val automapGroup = "com.sky.automap"
// AAR 版本表：唯一来源是 CarMapProject/versions.properties（全仓库只此一份）。
// 这里直接读该文件，不生成副本、不引脚本插件，IDE 不会红。
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

subprojects {
    // 统一坐标：同工程内的 project 依赖在 POM 中同样映射为 com.sky.automap:<模块名>
    group = automapGroup
    version = skyVersionOf(project.name)

    plugins.withId("com.android.library") {
        pluginManager.apply("maven-publish")

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        from(components["release"])
                        groupId = automapGroup
                        artifactId = project.name
                        version = skyVersionOf(project.name)
                    }
                }
                repositories {
                    maven {
                        name = "skyLocalRepo"
                        url = uri(rootProject.layout.projectDirectory.dir("../CarMapProject/maven-repo"))
                    }
                }
            }
        }
    }
}
