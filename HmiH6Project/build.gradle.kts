import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// ============================================================================
// HmiH6Project —— H6 车型 HMI 独立工程（产物：hmi-h6.aar）
//
// 车型专属的 UI 视觉与交互，只依赖：
//   - hmi-api（契约）
//   - infrastructure（基建层）
//   - SkyBusinessComponent 的业务组件（poi / navigation / route）
// 与 H9 工程完全独立：两个工程之间没有任何依赖，可独立开发、独立发版。
// 构建：./gradlew publish
// ============================================================================

// SkyBuild 全局共享配置（供本工程所有模块继承）
extra["skyBuild.appName"] = "HmiH6Project"
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
// AAR 版本表：唯一来源是 CarMapProject/versions.properties，由 ./sync-versions.py 同步到
// 本工程 gradle.properties 的 automap.version.* 键。这里直接读 Gradle 属性，不碰 extra、
// 不引脚本插件 —— IDE 对顶层语句的脚本分析不可靠，写得越少越不会红。
fun skyVersionOf(artifact: String): String =
    providers.gradleProperty("automap.version.$artifact").orNull
        ?: throw GradleException(
            "gradle.properties 缺少 automap.version.$artifact，请先执行 CarMapProject/sync-versions.py"
        )

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
