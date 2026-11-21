package com.sky.automap.map.api

/**
 * 地图 SDK 类型。
 *
 * 具体使用哪一种 SDK 由「壳工程」在打包时注入（H6 → 高德，H9 → 百度），
 * 业务组件层永远只面向 [IMapProvider] 编程，不感知厂商差异。
 */
enum class MapSdkType(val label: String) {
    AMAP("高德地图"),
    BAIDU("百度地图"),
}
