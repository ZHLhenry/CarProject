package com.sky.automap.map.api

import android.content.Context
import android.view.View

/**
 * 地图能力统一接口（契约包 `com.sky.automap.map.api`）。
 *
 * 约束：
 * - 业务组件层（poi / navigation / route）只依赖 map-api（本包 + 门面包），
 *   严禁直接依赖 impl-amap / impl-baidu；
 * - 本包只放接口与数据模型，**不含任何可变状态**；注入与取用门面在同一个 AAR 的
 *   `com.sky.automap.map.factory` 包（[com.sky.automap.map.factory.MapManager]）；
 * - 具体实现在打包阶段由壳工程注入。
 */
interface IMapProvider {

    /** 当前实现对应的 SDK 类型 */
    val sdkType: MapSdkType

    /** 当前实现绑定的 SDK 版本，如 "850" / "910"（由绑定模块上报，业务层只用于展示与日志） */
    val sdkVersion: String

    /**
     * 当前实现的能力集。
     *
     * 业务层做功能降级时**只看能力位**，不看 [sdkVersion]：
     * 新增一个 SDK 版本时会自动获得正确的降级行为，业务组件无需重新编译。
     */
    val capabilities: MapCapabilities

    /** 创建地图视图 */
    fun createMapView(context: Context): IMapView

    /** POI 搜索（关键字 + 可选中心点） */
    fun searchPoi(keyword: String, around: LatLng? = null): List<MapPoi>

    /** 路径规划，返回途经点序列 */
    fun planRoute(start: LatLng, end: LatLng): List<LatLng>

    /**
     * 多路线规划，默认退化为单条。
     *
     * 是否真的有多个方案由 [capabilities] 的 `supportsMultiRoute` 决定，
     * 因此老版本实现不需要覆写本方法。
     */
    fun planRoutes(start: LatLng, end: LatLng): List<List<LatLng>> =
        listOf(planRoute(start, end))
}

/** 地图视图能力（各厂商实现内部持有真正的 MapView） */
interface IMapView {

    /** 可挂载到布局上的真实 View */
    val view: View

    /** 移动到指定位置 */
    fun moveTo(position: LatLng, zoom: Float = DEFAULT_ZOOM)

    /** 添加标注 */
    fun addMarker(marker: MapMarker)

    /** 移除标注 */
    fun removeMarker(id: String)

    /** 清空标注 */
    fun clear()

    companion object {
        const val DEFAULT_ZOOM: Float = 15f
    }
}
