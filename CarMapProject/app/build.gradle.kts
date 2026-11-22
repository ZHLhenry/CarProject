import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.sky.android.application)
    // Flavor 场景下注册 skyBuild 共享扩展
    alias(libs.plugins.sky.android.application.flavors)
}

// 依赖的其它独立工程发布版本（../CarMapProject/maven-repo 中的 com.sky.automap:*），
// 版本来自本工程根目录的 versions.properties（全仓库唯一版本表）
fun skyVersionOf(artifact: String): String {
    // 版本表是纯文本，直接按行读（不用 java.util.Properties：kts 里 `java` 会被
    // JavaPluginExtension 遮蔽）；注释与空行跳过
    val key = "$artifact="
    return rootProject.file("versions.properties")
        .takeIf { it.isFile }
        ?.readLines()
        ?.firstOrNull { it.trim().startsWith(key) }
        ?.substringAfter("=")?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("CarMapProject/versions.properties 里没有 $artifact 这一行")
}

// ===========================================================================
// 渠道（APP_BUILD_ID）
//
// 唯一来源：同目录 app-builds.json —— 车型 / 芯片 / 产品 / 屏型 / 分辨率 / 地图厂商与版本。
// 配置阶段直接解析该文件，**不再**往 gradle.properties 展平（渠道会一直增加，
// 展平后 properties 里就是几百行机器生成内容，改一处还得记得同步）。
// 运行期经 BuildConfig → ChannelConfig 取用。
//
// 文件里三段：carModels（车型 → 厂商 + 允许版本）、builds（渠道）、defaultChannel（默认渠道）。
// 本脚本不硬编码任何车型/厂商映射，全部从 carModels 读：
//   车型 → hmi-<车型> + src/<车型>/{java,res}
//   厂商 → map-impl-<厂商>
//   版本 → map-sdk-<厂商>-<版本>
// 一个 APK 只打一份地图 SDK：版本可按渠道切，厂商由车型决定（车型源集的
// PlatformAssembler 只 import 本厂商的实现），渠道写了别的厂商会在配置阶段直接报错。
//
// 用法（flavor 名 = app-builds.json 里的键）：
//   ./gradlew assembleDebug                                          # 全部渠道
//   ./gradlew assembleE245_h6_8295_ceiling_1280x720_amap910Debug     # 单个渠道
//
// 三道防线保证「声明 = 实际打包」：
//   1. 构建期：按渠道注入对应 SDK，并把渠道参数写进 BuildConfig；
//   2. PlatformAssembler 按 BuildConfig 的版本选 binding（不再硬编码）；
//   3. 运行期：影子 SDK 的版本标定（AmapSdk.mode / BaiduSdk.mode）不一致立刻抛异常。
// ===========================================================================
data class AppBuild(
    val id: String,
    val carModel: String,
    val chip: String,
    val product: String,
    val screenType: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val mapVendor: String,
    val mapVersion: String,
)

/**
 * 车型元数据（app-builds.json 的 carModels 段）。
 *
 * 一个车型固定一个地图厂商 + 一组可用 SDK 版本。这个约束**只作为数据写在 JSON 里**，
 * 加车型 = JSON 里加一段 carModels、发一个 hmi-<车型> AAR，脚本一行都不用动。
 */
data class CarModel(
    /** 车型名（小写）：源集目录 src/<name> 与 AAR 坐标 hmi-<name> 都用它 */
    val name: String,
    val mapVendor: String,
    val mapVersions: Set<String>,
)

/** app-builds.json 的解析结果 */
data class Channels(
    val carModels: Map<String, CarModel>,
    val builds: Map<String, AppBuild>,
    /** 默认渠道：assembleDefault / installDefault 打的就是它 */
    val defaultChannel: String,
)

/** 渠道表文件：工程根目录（CarMapProject/）下的 app-builds.json */
val appBuildsJson: File = rootProject.file("app-builds.json")

/** 屏型白名单（app-builds.json 的 screenType 用） */
val screenTypes: Set<String> = setOf("FRONT", "REAR", "CEILING", "ARMREST")

/**
 * 解析 app-builds.json 的 carModels + builds 两段。
 *
 * 缺字段 / 类型不对 / 屏型拼错 / 厂商与车型不符 / 版本不在车型允许范围，
 * 全部在配置阶段直接抛，报错里带渠道 ID 或车型名，
 * 不用等到 dex 出来才发现 BuildConfig 里是个错值。
 */
/** 取一段 JSON 对象；不是对象时抛带位置的 GradleException（而不是后面的 ClassCastException） */
@Suppress("UNCHECKED_CAST")
fun jsonObject(value: Any?, where: String): Map<String, Any?> =
    value as? Map<String, Any?>
        ?: throw GradleException("$where 必须是 JSON 对象，实际 $value")

fun readChannels(file: File): Channels {
    if (!file.isFile) {
        throw GradleException("找不到渠道表 ${file.absolutePath}")
    }
    val json = jsonObject(JsonSlurper().parse(file), file.name)

    // as? Map<String, Map<String, Any?>> 因泛型擦除只校验到「是 Map」，
    // 所以每条渠道 / 车型都再过一次 jsonObject()，错误才落成带 ID 的提示
    val builds = jsonObject(json["builds"], "${file.name} 的 builds")
    val models = jsonObject(json["carModels"], "${file.name} 的 carModels")
    val defaultChannel = json["defaultChannel"]?.toString()?.takeIf { it.isNotBlank() }
        ?: throw GradleException("${file.name} 缺少 defaultChannel（默认渠道，assembleDefault 用它）")

    val carModels = models.entries.associate { (rawName, rawValue) ->
        val name = rawName.lowercase()
        val raw = jsonObject(rawValue, "carModels.$name")
        val vendor = raw["mapVendor"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw GradleException("carModels.$name 缺少 mapVendor")
        val versions = (raw["mapVersions"] as? List<*>)
            ?.map { it.toString() }?.filter { it.isNotBlank() }?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: throw GradleException("carModels.$name 缺少 mapVersions，如 [\"850\", \"910\"]")
        name to CarModel(name = name, mapVendor = vendor.uppercase(), mapVersions = versions)
    }

    val parsed = builds.entries.associate { (id, rawValue) ->
        val raw = jsonObject(rawValue, "builds.$id")

        fun text(field: String): String =
            raw[field]?.toString()?.takeIf { it.isNotBlank() }
                ?: throw GradleException("渠道 $id 缺少字段 $field（app-builds.json）")

        fun number(field: String): Int =
            text(field).toIntOrNull()
                ?: throw GradleException("渠道 $id 的 $field 必须是整数，实际 ${raw[field]}")

        id to AppBuild(
            id = id,
            carModel = text("carModel").uppercase(),
            chip = text("chip"),
            product = text("product"),
            screenType = text("screenType").uppercase(),
            screenWidth = number("screenWidth"),
            screenHeight = number("screenHeight"),
            mapVendor = text("mapVendor").uppercase(),
            mapVersion = text("mapVersion"),
        )
    }

    // 逐条渠道校验：屏型、车型是否声明、厂商是否与车型一致、版本是否在该车型允许范围
    val errors = mutableListOf<String>()
    parsed.values.forEach { build ->
        if (build.screenType !in screenTypes) {
            errors += "${build.id}: screenType 只能是 $screenTypes，实际 ${build.screenType}"
        }
        val model = carModels[build.carModel.lowercase()]
        if (model == null) {
            errors += "${build.id}: carModel「${build.carModel}」没有在 carModels 里声明" +
                "（可选：${carModels.keys.joinToString()}）"
            return@forEach
        }
        if (build.mapVendor != model.mapVendor) {
            errors += "${build.id}: 车型 ${model.name} 固定用 ${model.mapVendor} 地图，" +
                "渠道却写了 ${build.mapVendor}（一个 APK 只打一份 SDK，厂商由车型的装配代码决定）"
        }
        if (build.mapVersion !in model.mapVersions) {
            errors += "${build.id}: ${model.mapVendor} 的 mapVersion 只能是 " +
                "${model.mapVersions.sorted()}，实际 ${build.mapVersion}"
        }
    }
    if (errors.isNotEmpty()) {
        throw GradleException("app-builds.json 校验失败：\n  " + errors.joinToString("\n  "))
    }

    if (defaultChannel !in parsed) {
        throw GradleException(
            "app-builds.json 校验失败：\n  defaultChannel「$defaultChannel」不在 builds 里" +
                "（可选：${parsed.keys.joinToString()}）"
        )
    }

    return Channels(carModels = carModels, builds = parsed, defaultChannel = defaultChannel)
}

val channels: Channels = readChannels(appBuildsJson)

/** 渠道 ID → 参数（builds 段），flavor 名就是这个键 */
val appBuilds: Map<String, AppBuild> = channels.builds

/** 默认渠道：assembleDefault / installDefault 打的就是它（builds 里必须存在，解析时已校验） */
val defaultChannel: String = channels.defaultChannel

/** 厂商实现 AAR 坐标：map-impl-<厂商小写>（加厂商 = 发这个 AAR + versions.properties 加一行） */
fun mapImplArtifact(vendor: String): String = "map-impl-${vendor.lowercase()}"

/** 该渠道唯一一份 SDK AAR 坐标：map-sdk-<厂商小写>-<版本>（版本合法性已在 carModels 里校验） */
fun mapSdkArtifact(vendor: String, version: String): String =
    "map-sdk-${vendor.lowercase()}-$version"

/** flavor 名 = 渠道 ID，直接按 ID 取参数；取不到就是配置错误 */
fun appBuildOf(flavor: String): AppBuild =
    appBuilds[flavor] ?: throw GradleException(
        "未知渠道「$flavor」，可选：${appBuilds.keys.joinToString()}"
    )

android {
    namespace = "com.sky.automap"

    /**
     * 一个渠道 = 一个 productFlavor，**flavor 名就是渠道 ID**。
     *
     * 于是变体名、任务名、APK 文件名全都自带渠道，IDE 里点哪个任务打哪个渠道一目了然：
     *   assembleE245_h6_8295_front_1920x720_amap850Debug
     *   assembleE245_h6_8295_ceiling_1280x720_amap910Debug
     *   assembleE245_h9_6155_front_2560x1440_baiduv22Release
     *
     * 车型差异（HMI 源码、资源、applicationId）仍按 carModel 走：
     * 用 sourceSets 把 src/<车型>/java、src/<车型>/res 挂到该渠道的 flavor 源集上，
     * PlatformAssembler「同包同名、编译期二选一」的机制完全不变。
     *
     * 注意：同车型的多个渠道 applicationId 相同（com.sky.automap.h6），
     * 装同一台设备会互相覆盖 —— 渠道包本就是同一产品的不同形态，属于预期。
     *
     * applicationId 在 androidComponents.onVariants 里按车型覆盖，因为
     * sky.android.application 约定插件会以 skyBuild.applicationId（基准包名）统一赋值。
     */
    flavorDimensions += "channel"

    productFlavors {
        appBuilds.values.forEach { build ->
            create(build.id) {
                dimension = "channel"
            }
        }
    }

    sourceSets {
        appBuilds.values.forEach { build ->
            val model = build.carModel.lowercase()
            getByName(build.id) {
                // AGP 9 起 srcDir()/srcDirs() 已废弃，直接往 directories（相对路径集合）里加。
                // kotlin 目录集是对 java 的独立快照，只加 java 的话 Kotlin 编译任务看不到这些 .kt
                java.directories.add("src/$model/java")
                kotlin.directories.add("src/$model/java")
                res.directories.add("src/$model/res")
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val flavor = variant.flavorName
            ?: throw GradleException("变体 ${variant.name} 没有 channel flavor，无法确定渠道")
        val build = appBuildOf(flavor)

        // 包名按车型：H6 系列 com.sky.automap.h6，H9 系列 com.sky.automap.h9
        variant.applicationId.set("com.sky.automap.${build.carModel.lowercase()}")

        // ---- 渠道参数 → BuildConfig（运行期经 ChannelConfig 读取）----
        // String 由这里统一加引号并转义：JSON 里万一写了引号/反斜杠，
        // 也会生成合法的 BuildConfig.java，而不是在生成物里报语法错（很难定位）
        fun bc(name: String, type: String, value: String, comment: String) {
            val literal = if (type == "String") {
                "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            } else {
                value
            }
            variant.buildConfigFields?.put(
                name,
                com.android.build.api.variant.BuildConfigField(type, literal, comment)
            )
        }
        bc("APP_BUILD_ID", "String", build.id, "渠道 ID（app-builds.json 的键）")
        bc("APP_CAR_MODEL", "String", build.carModel, "车型")
        bc("APP_CHIP", "String", build.chip, "车机芯片")
        bc("APP_PRODUCT", "String", build.product, "产品名")
        bc("APP_SCREEN_TYPE", "String", build.screenType, "屏型 FRONT/REAR/CEILING/ARMREST")
        bc("APP_SCREEN_WIDTH", "int", build.screenWidth.toString(), "屏宽（像素）")
        bc("APP_SCREEN_HEIGHT", "int", build.screenHeight.toString(), "屏高（像素）")
        bc("APP_MAP_VENDOR", "String", build.mapVendor, "地图厂商 AMAP/BAIDU")
        bc("APP_MAP_VERSION", "String", build.mapVersion, "地图 SDK 版本")

        // APK 文件名不再需要额外改名：SkyBuildLogic 的 renameAndOpen<Variant>Apk 输出
        //   CarMap_<flavor>_<buildType>_v<versionName>_<versionCode>_<时间戳>.apk
        // 而 flavor 名就是渠道 ID，产物天然带渠道信息：
        //   CarMap_e245_h6_8295_front_1920x720_amap850_debug_v1.0.0_100_<时间戳>.apk
    }
}

dependencies {
    // 日志实现：SkyMVVMLib 对 xlog 是 compileOnly（不进 AAR、不进 POM，设计上要求宿主提供），
    // 漏掉会在 CarMapApplication.onCreate → InfraInitializer.initMvvm → SkyMVVMLibConfig
    // 处直接 NoClassDefFoundError: com.elvishew.xlog.printer.Printer。
    implementation(libs.xlog)

    // 基建层 AAR（SkyMVVM / SkyWidget / SkyMultiStateLayout 的统一出口）
    implementation("com.sky.automap:infrastructure:${skyVersionOf("infrastructure")}")

    // HMI 契约 AAR
    implementation("com.sky.automap:hmi-api:${skyVersionOf("hmi-api")}")

    // 对外 AIDL 服务端（Stub 实现 + <service> 声明，manifest 由它合并进来）
    // 用 project 而不是 AAR：它只服务本工程，没有独立发布的意义；
    // 它自己 implementation 引入 car-api，所以这里不需要再声明 car-api 版本。
    implementation(project(":service"))

    // 地图契约 + 门面 AAR（接口/数据模型 + MapManager；具体实现由各 Flavor 注入）
    implementation("com.sky.automap:map-api:${skyVersionOf("map-api")}")

    // 每个渠道各自：HMI 车型工程 + 地图厂商实现 + 唯一一份 SDK
    // - 车型     → hmi-<车型>（app-builds.json 的 carModels）
    // - 地图厂商 → map-impl-<vendor>
    // - 地图版本 → map-sdk-<vendor>-<version>（该渠道唯一一份，dex 里不会出现两份）
    // - 厂商实现里的 SDK 依赖是 compileOnly，不传递，所以这里必须显式给 SDK
    appBuilds.values.forEach { build ->
        val flavor = build.id
        val hmi = "hmi-${build.carModel.lowercase()}"
        val impl = mapImplArtifact(build.mapVendor)
        val sdk = mapSdkArtifact(build.mapVendor, build.mapVersion)
        add("${flavor}Implementation", "com.sky.automap:$hmi:${skyVersionOf(hmi)}")
        add("${flavor}Implementation", "com.sky.automap:$impl:${skyVersionOf(impl)}")
        add("${flavor}Implementation", "com.sky.automap:$sdk:${skyVersionOf(sdk)}")
    }
}

// ===========================================================================
// 默认渠道的固定入口
//
// IDE 的 Build Variants 默认项由 Android Studio 按变体名字母序自己挑，AGP 也没有
// 「默认变体」这个 API，脚本改不了它。所以把「默认打哪个渠道」固化成任务：
//   ./gradlew :app:assembleDefault         # 默认渠道的 Debug APK
//   ./gradlew :app:installDefault          # 装默认渠道到设备
//   ./gradlew :app:assembleDefaultRelease  # 默认渠道的 Release APK
// 换默认渠道只需改 app-builds.json 的 defaultChannel —— 可提交、可复现。
//
// 渠道 ID → 变体/任务名的推导只在这里做一次（AGP 只把首字母大写），
// 外部脚本（build-all.py）一律调这些别名任务，不自己拼名字。
// ===========================================================================
fun variantName(channel: String, buildType: String): String =
    channel.replaceFirstChar { it.uppercase() } + buildType

tasks.register("assembleDefault") {
    group = "build"
    description = "打默认渠道 $defaultChannel 的 Debug 包"
    dependsOn("assemble" + variantName(defaultChannel, "Debug"))
}

tasks.register("installDefault") {
    group = "install"
    description = "把默认渠道 $defaultChannel 的 Debug 包装到设备"
    dependsOn("install" + variantName(defaultChannel, "Debug"))
}

tasks.register("assembleDefaultRelease") {
    group = "build"
    description = "打默认渠道 $defaultChannel 的 Release 包"
    dependsOn("assemble" + variantName(defaultChannel, "Release"))
}
