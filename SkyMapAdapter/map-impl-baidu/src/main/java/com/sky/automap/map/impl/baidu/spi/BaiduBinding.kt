package com.sky.automap.map.impl.baidu.spi

import android.content.Context
import android.view.View
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities

/**
 * 百度 SDK 版本绑定接口（SPI）。
 *
 * 与高德侧完全对称：厂商层不含 SDK 版本差异，每个版本一个包
 * （`binding.v21` / `binding.v22`）实现本接口。
 *
 * 注意区分同名文件：本文件（`spi/BaiduBinding.kt`，**单数**）是契约；
 * 按版本取实例的工厂在 `binding/BaiduBindingFactory.kt`。
 */
interface BaiduBinding {

    /** SDK 版本号，如 "v21" / "v22" */
    val sdkVersion: String

    val capabilities: MapCapabilities

    /** 初始化：v21 与 v22 的隐私合规调用顺序不同，差异收敛在这里 */
    fun initialize(context: Context, ak: String)

    fun createNativeMap(context: Context): BaiduNativeMap

    fun searchPoi(keyword: String, around: LatLng?): List<RawBaiduPoi>

    fun planRoute(start: LatLng, end: LatLng): List<LatLng>

    /** 多路线规划，默认退化为单条（v22 覆写） */
    fun planRoutes(start: LatLng, end: LatLng): List<List<LatLng>> =
        listOf(planRoute(start, end))

    fun release()
}

/** 百度原生地图对象的统一外观 */
interface BaiduNativeMap {

    val view: View

    fun moveTo(latitude: Double, longitude: Double, zoom: Float)

    fun addMarker(id: String, title: String, latitude: Double, longitude: Double)

    fun removeMarker(id: String)

    fun clearMarkers()

    fun onCreate()

    fun onResume()

    fun onPause()

    fun onDestroy()
}

/** 百度原始 POI 结构（v21 用 `uid/name/addr`，v22 用 `poiId/title/detail`） */
data class RawBaiduPoi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)
