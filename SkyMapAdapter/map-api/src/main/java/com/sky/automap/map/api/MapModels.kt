package com.sky.automap.map.api

/** 经纬度（GCJ-02 / BD-09 由具体实现负责坐标转换） */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

/** 地图标注点 */
data class MapMarker(
    val id: String,
    val position: LatLng,
    val title: String,
)

/** POI 搜索结果 */
data class MapPoi(
    val id: String,
    val name: String,
    val address: String,
    val position: LatLng,
)
