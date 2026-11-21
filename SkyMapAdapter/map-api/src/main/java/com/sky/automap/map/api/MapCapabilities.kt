package com.sky.automap.map.api

/**
 * 地图 SDK 能力集——「版本差异」对业务层暴露的唯一形态。
 *
 * 背景：同一个厂商的不同 SDK 版本（高德 850 / 910、百度 v21 / v22）在 API 与功能上并不等价，
 * 例如 910 才支持离线地图与多路线，850 则没有。但业务组件（poi / navigation / route）
 * **不允许**出现 `if (sdkVersion == "850")` 这类判断，否则每新增一个版本都要回头改三个组件。
 *
 * 约定：
 * - 业务层只按「能力位」降级，如 `if (capabilities.supportsOfflineMap) 显示离线入口`；
 * - 能力位由各版本绑定（`map-impl-*.binding.v*`）如实上报，与实际 SDK 保持一致；
 * - 新增能力位时给默认值 `false`，保证二进制兼容（老 binding 不需要立刻改动）。
 */
data class MapCapabilities(

    /** 厂商标识，如 "amap" / "baidu" */
    val sdkName: String,

    /** SDK 版本号，如 "850" / "910" / "v21" / "v22" */
    val sdkVersion: String,

    /** 3D 地图（850 不支持） */
    val supports3D: Boolean = false,

    /** 实时路况图层 */
    val supportsTrafficLayer: Boolean = false,

    /** 离线地图（850 不支持） */
    val supportsOfflineMap: Boolean = false,

    /** 多路线规划（850 只返回单条推荐路线） */
    val supportsMultiRoute: Boolean = false,

    /** 室内地图 */
    val supportsIndoorMap: Boolean = false,
) {

    /** 形如 `amap-910`，用于日志、调试面板与埋点 */
    val tag: String get() = "$sdkName-$sdkVersion"

    /** 人类可读的能力摘要，如 `3D · 路况 · 离线 · 多路线` */
    fun describe(): String {
        val items = buildList {
            if (supports3D) add("3D")
            if (supportsTrafficLayer) add("路况")
            if (supportsOfflineMap) add("离线")
            if (supportsMultiRoute) add("多路线")
            if (supportsIndoorMap) add("室内")
        }
        return if (items.isEmpty()) "基础能力" else items.joinToString(" · ")
    }

    companion object {

        /** 未接入任何地图时的占位能力集 */
        @JvmField
        val NONE: MapCapabilities = MapCapabilities(sdkName = "none", sdkVersion = "0")
    }
}
