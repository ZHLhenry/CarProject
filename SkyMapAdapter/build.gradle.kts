import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// ============================================================================
// SkyMapAdapter —— 地图适配层（独立工程）
//
// 产物（发布到 ../CarMapProject/maven-repo，共 8 个模块）：
//   com.sky.automap:map-api            → 契约层 / 标准数据结构
//   com.sky.automap:map-factory        → 门面（注入、取用、能力查询）
//   com.sky.automap:map-impl-amap      → 高德：厂商公共层 + 850/910 两段适配（包区分）
//   com.sky.automap:map-impl-baidu     → 百度：厂商公共层 + v21/v22 两段适配（包区分）
//   com.sky.automap:map-sdk-amap-850   → 高德 850 影子 SDK
//   com.sky.automap:map-sdk-amap-910   → 高德 910 影子 SDK + 编译基线（三版并集）
//   com.sky.automap:map-sdk-baidu-v21  → 百度 v21 影子 SDK
//   com.sky.automap:map-sdk-baidu-v22  → 百度 v22 影子 SDK + 编译基线（两版并集）
//
// 架构红线：map-api / map-factory 不依赖任何厂商 SDK；map-impl-* 只 compileOnly 引用基线 SDK；
// 真正进 dex 的那一份由壳工程按车型注入。
// 构建：./gradlew verifyMapSdkChain && ./gradlew publish
// ============================================================================

// SkyBuild 全局共享配置（供本工程所有模块继承）
extra["skyBuild.appName"] = "SkyMapAdapter"
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

// ============================ SDK 继承链校验 ==================================
// 版本适配改成「包」之后，编译期不再按版本隔离 API（同一模块只有一条 classpath），
// 所以「被新版移除的 API 是否还有人调」必须靠静态检查兜住。规则见 buildSrc 里的
// VerifyMapSdkChainTask：调用点必须落在被该版本 binding 覆写过的方法里。
//
//   ./gradlew verifyMapSdkChain
//
// CI 里应该在 publish 之前跑一次；CarMapProject/build-all.py 已包含。

fun registerChainCheck(
    taskName: String,
    chain: String,
    sdkVersions: List<String>,
    bindings: Map<String, String>,
    sdkPrefixes: List<String>,
    shadowProject: String,
    implProject: String,
    semanticBreaks: Map<String, List<String>>,
) {
    val verify = tasks.register<buildlogic.VerifyMapSdkChainTask>(taskName) {
        group = "verification"
        description = "校验 $chain 继承链：被新版移除的 API 是否仍会被调用"
        chainName.set(chain)
        versions.set(sdkVersions)
        bindingClasses.set(bindings)
        sdkPackages.set(sdkPrefixes)
        mustOverride.set(semanticBreaks)
        // class 输出目录由各模块的 compileDebugKotlin 任务决定（不同 AGP/KGP 版本路径不同），
        // 所以从任务输出里取，而不是硬编码 build/intermediates 路径
        shadowClasses.from(
            provider { project(shadowProject).tasks.named("compileDebugKotlin").get().outputs.files },
        )
        implClasses.from(
            provider { project(implProject).tasks.named("compileDebugKotlin").get().outputs.files },
        )
        reportFile.set(layout.buildDirectory.file("reports/sdk-chain/$chain.md"))
        dependsOn("$shadowProject:compileDebugKotlin", "$implProject:compileDebugKotlin")
    }
    tasks.named("verifyMapSdkChain").configure { dependsOn(verify) }
}

tasks.register("verifyMapSdkChain") {
    group = "verification"
    description = "校验全部地图 SDK 继承链（CI 必跑）"
}

registerChainCheck(
    taskName = "verifyAmapChain",
    chain = "amap",
    sdkVersions = listOf("850", "910"),
    bindings = mapOf(
        "850" to "com/sky/automap/map/impl/amap/binding/v850/Amap850Binding",
        "910" to "com/sky/automap/map/impl/amap/binding/v910/Amap910Binding",
    ),
    sdkPrefixes = listOf("com/amap/api/"),
    shadowProject = ":map-sdk-amap-910",
    implProject = ":map-impl-amap",
    // 910 的 initialize 签名没变，但要求先声明隐私合规 → 静态 API 面 diff 抓不到，显式声明
    semanticBreaks = mapOf("910" to listOf("initialize")),
)

registerChainCheck(
    taskName = "verifyBaiduChain",
    chain = "baidu",
    sdkVersions = listOf("v21", "v22"),
    bindings = mapOf(
        "v21" to "com/sky/automap/map/impl/baidu/binding/v21/BaiduV21Binding",
        "v22" to "com/sky/automap/map/impl/baidu/binding/v22/BaiduV22Binding",
    ),
    sdkPrefixes = listOf("com/baidu/mapapi/"),
    shadowProject = ":map-sdk-baidu-v22",
    implProject = ":map-impl-baidu",
    semanticBreaks = mapOf("v22" to listOf("initialize")),
)

// ============================ AAR 发布约定 ====================================
// groupId 固定 com.sky.automap，artifactId = 模块名，版本取CarMapProject 下的 versions.properties
// 中该 artifact 那一行；仓库位于 CarMapProject/maven-repo（与其它工程共享，免私服即可联调）。
val automapGroup = "com.sky.automap"
// AAR 版本表：唯一来源是 CarMapProject/versions.properties，由 ./versions.py sync 同步到
// 本工程 gradle.properties 的 automap.version.* 键。这里直接读 Gradle 属性，不碰 extra、
// 不引脚本插件 —— IDE 对顶层语句的脚本分析不可靠，写得越少越不会红。
fun skyVersionOf(artifact: String): String =
    providers.gradleProperty("automap.version.$artifact").orNull
        ?: throw GradleException(
            "gradle.properties 缺少 automap.version.$artifact，请先执行 CarMapProject/versions.py sync"
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
