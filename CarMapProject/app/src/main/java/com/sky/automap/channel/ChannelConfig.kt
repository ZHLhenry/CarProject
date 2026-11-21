package com.sky.automap.channel

import com.sky.automap.BuildConfig

/**
 * 渠道（APP_BUILD_ID）的运行期唯一入口。
 *
 * 数据链路：
 * ```
 * app-builds.json（唯一来源，人只改这里）
 *   → app/build.gradle.kts 配置阶段直接解析，注入 BuildConfig.APP_*
 *   → 这里
 * ```
 *
 * 渠道参数是**编译期常量**，所以这里用 `get()` 而不是 `by lazy`：
 * 没有反射、没有 IO、没有初始化顺序问题，R8 也能把常量折叠进调用点。
 *
 * 用法（根据渠道加载不同内容）：
 * ```kotlin
 * when (ChannelConfig.screenType) {
 *     ScreenType.FRONT   -> loadFrontLayout()
 *     ScreenType.REAR    -> loadRearLayout()
 *     ScreenType.CEILING -> loadCeilingLayout()
 *     ScreenType.ARMREST -> loadArmrestLayout()
 * }
 * if (ChannelConfig.screenWidth >= 2560) loadHighResAssets()
 * ```
 */
object ChannelConfig {

    /** 渠道 ID：`app-builds.json` 的键，形如 `e245_h6_8295_front_1920x720_amap850` */
    val buildId: String get() = BuildConfig.APP_BUILD_ID

    /** 车型：`H6` / `H9` */
    val carModel: String get() = BuildConfig.APP_CAR_MODEL

    /** 车机芯片：`8295` / `6155` / `8155` ... */
    val chip: String get() = BuildConfig.APP_CHIP

    /** 产品名：`E245` ... */
    val product: String get() = BuildConfig.APP_PRODUCT

    /** 屏幕位置 */
    val screenType: ScreenType get() = ScreenType.of(BuildConfig.APP_SCREEN_TYPE)

    val screenWidth: Int get() = BuildConfig.APP_SCREEN_WIDTH

    val screenHeight: Int get() = BuildConfig.APP_SCREEN_HEIGHT

    /** 地图厂商 */
    val mapVendor: MapVendor get() = MapVendor.of(BuildConfig.APP_MAP_VENDOR)

    /**
     * 地图 SDK 版本。取值由该车型的 `carModels.<车型>.mapVersions` 限定
     * （当前：高德 `850`/`910`，百度 `v21`/`v22`），非法值在构建期就会被挡下。
     */
    val mapVersion: String get() = BuildConfig.APP_MAP_VERSION

    /** 一行摘要，用于日志 / 关于页 / 调试面板 */
    fun summary(): String =
        "APP_BUILD_ID=$buildId car=${carModel} chip=${chip} product=${product} " +
            "screen=${screenType.name}(${screenWidth}x${screenHeight}) " +
            "map=${mapVendor.name}:${mapVersion}"
}

/**
 * 屏幕位置。
 *
 * 枚举名必须与 `app-builds.json` 里 `screenType` 的取值一致 ——
 * `app/build.gradle.kts` 的 `screenTypes` 白名单在构建期校验，写错直接报错。
 */
enum class ScreenType {
    /** 前排中控 */
    FRONT,

    /** 后排屏 */
    REAR,

    /** 吸顶屏 */
    CEILING,

    /** 扶手的控制屏 */
    ARMREST,
    ;

    companion object {
        fun of(raw: String): ScreenType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalStateException("未知屏型：$raw（可选：${entries.joinToString { it.name }}）")
    }
}

/**
 * 地图厂商。车型固定一个厂商（`carModels.<车型>.mapVendor`），渠道不能突破。
 */
enum class MapVendor {
    /** 高德 */
    AMAP,

    /** 百度 */
    BAIDU,
    ;

    companion object {
        fun of(raw: String): MapVendor =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalStateException("未知地图厂商：$raw（可选：${entries.joinToString { it.name }}）")
    }
}
