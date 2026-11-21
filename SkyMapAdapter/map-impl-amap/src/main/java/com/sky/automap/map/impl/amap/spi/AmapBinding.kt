package com.sky.automap.map.impl.amap.spi

import android.content.Context
import android.view.View
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities

/**
 * 高德 SDK 版本绑定接口（SPI）。
 *
 * 这是「厂商层」与「版本绑定层」之间的唯一约定：
 * - 本模块（map-impl-amap）只写与 SDK 版本无关的公共逻辑，**不 import 任何 `com.amap.api.*`**；
 * - 每个 SDK 版本各有一个包（`binding.v850 / v910`）实现本接口，
 *   只有那里才出现真实 SDK 的类名、初始化流程与回调形态；
 * - 壳工程按车型选择其中一个 binding，通过 `AmapMapProvider(binding)` 注入。
 *
 * 只允许 `binding.v*` 包下的类实现本接口；业务组件与 HMI 不得依赖本包下任何类型。
 *
 * 注意区分同名文件：本文件（`spi/AmapBinding.kt`，**单数**）是契约；
 * 按版本取实例的工厂在 `binding/AmapBindingFactory.kt`。前者回答"实现什么"，后者回答"用哪个版本"。
 */
interface AmapBinding {

    /** SDK 版本号，如 "850" / "910" */
    val sdkVersion: String

    /** 该版本如实具备的能力，供业务层按能力降级 */
    val capabilities: MapCapabilities

    /**
     * SDK 初始化。
     *
     * 版本差异集中于此：850 只需 Key；910 要求先完成隐私合规声明再初始化，
     * 这些差异不需要上层感知。
     */
    fun initialize(context: Context, apiKey: String)

    /** 创建厂商原生地图对象 */
    fun createNativeMap(context: Context): AmapNativeMap

    /**
     * POI 检索。
     *
     * 850 是异步回调式 API，910 是同步返回式 API；
     * 绑定模块负责把两者都收敛成本方法的同步返回语义。
     */
    fun searchPoi(keyword: String, around: LatLng?): List<RawPoi>

    /** 路径规划，返回途经点序列（850 只返回单条推荐路线） */
    fun planRoute(start: LatLng, end: LatLng): List<LatLng>

    /**
     * 多路线规划，默认退化为单条。
     *
     * 只有支持多路线的版本（如 910）才覆写；上层用
     * `capabilities.supportsMultiRoute` 判断是否展示「多方案」入口，
     * 因此老版本 binding 不需要为空能力写任何代码。
     */
    fun planRoutes(start: LatLng, end: LatLng): List<List<LatLng>> =
        listOf(planRoute(start, end))

    /** 释放 SDK 持有的资源 */
    fun release()
}

/**
 * 厂商原生地图对象的统一外观。
 *
 * 只回答「怎么调 SDK 的地图」——不含任何业务语义（业务语义在 [com.sky.automap.map.impl.amap.AmapMapView]）。
 */
interface AmapNativeMap {

    /** 可挂载到布局上的真实 View */
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

/**
 * 厂商原始 POI 结构。
 *
 * 不同 SDK 版本的字段命名与层级各不相同（850 是 `title/snippet`，910 是 `name/address`），
 * 因此 binding 一律返回本结构，由厂商层统一归一为 `MapPoi`。
 */
data class RawPoi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)
