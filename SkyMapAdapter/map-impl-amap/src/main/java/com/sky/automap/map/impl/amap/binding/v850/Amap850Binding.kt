package com.sky.automap.map.impl.amap.binding.v850

import android.content.Context
import android.view.View
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.Marker
import com.amap.api.maps.MarkerOptions
import com.amap.api.services.LatLonPoint
import com.amap.api.services.PoiSearch
import com.amap.api.services.RouteSearch
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities
import com.sky.automap.map.impl.amap.spi.AmapBinding
import com.sky.automap.map.impl.amap.spi.AmapNativeMap
import com.sky.automap.map.impl.amap.spi.RawPoi

/**
 * 高德 **850** 版本绑定：版本链的基类（包 `binding.v850`）。
 *
 * 设计要点：
 * - 类与需要被覆写的方法都是 `open`，因为 910 / 以后的 920 会按需 override；
 * - 这里写的是 850 就有的能力（初始化、建图 + 路况图层、异步检索、单路线、标注、生命周期），
 *   910 的增量与改动**不写在这里**，否则会污染 850 行为；
 * - 被 910 改掉的 API（`MapView(context)`、`searchPOIAsyn`、回调式路线）只出现在
 *   本类里，910 必须覆写对应方法；这是「继承链」唯一的运行期风险点，
 *   规避办法是把这类调用限制在可覆写的方法内，且不放进构造函数。
 *
 * 一句话：本类 = 全链共享的公共适配代码，不是「850 专属」。
 * （650 已不再支持，链路从 850 起，850 因此承接了原来 650 的基类角色。）
 */
open class Amap850Binding : AmapBinding {

    override val sdkVersion: String get() = VERSION

    override val capabilities: MapCapabilities
        get() = MapCapabilities(
            sdkName = "amap",
            sdkVersion = sdkVersion,
            supportsTrafficLayer = true,
        )

    override fun initialize(context: Context, apiKey: String) {
        MapsInitializer.setApiKey(apiKey)
        MapsInitializer.initialize(context)
    }

    override fun createNativeMap(context: Context): AmapNativeMap =
        Amap850NativeMap(MapView(context))

    override fun searchPoi(keyword: String, around: LatLng?): List<RawPoi> {
        var result: List<RawPoi> = emptyList()
        PoiSearch(keyword).searchPOIAsyn(around?.toLatLonPoint()) { poiResult, code ->
            if (code == SUCCESS_CODE) {
                result = poiResult?.pois.orEmpty().map {
                    RawPoi(
                        id = it.uid,
                        name = it.title,
                        address = it.snippet,
                        latitude = it.point.latitude,
                        longitude = it.point.longitude,
                    )
                }
            }
        }
        return result
    }

    override fun planRoute(start: LatLng, end: LatLng): List<LatLng> {
        var path: List<LatLng> = listOf(start, end)
        RouteSearch().calculateDriveRoute(start.toLatLonPoint(), end.toLatLonPoint()) { route, code ->
            if (code == SUCCESS_CODE) {
                path = route?.points.orEmpty().map { LatLng(it.latitude, it.longitude) }
            }
        }
        return path
    }

    override fun release() = Unit

    private companion object {
        const val VERSION = "850"

        const val SUCCESS_CODE = 1000
    }
}

/**
 * 850 的原生地图外观：`AMap` 的标注 API 需要传 [MarkerOptions] / [Marker] 实例，
 * 因此这里维护一份「业务 id → 原生 Marker」映射。
 *
 * 910 的标注 API 直接带业务 id，但那属于「新增能力」，不影响这段代码继续可用——
 * 继承链共享的正是这类与版本无关的搬运逻辑。
 */
open class Amap850NativeMap(private val mapView: MapView) : AmapNativeMap {

    /** 子类可在地图创建后做版本特有配置 */
    protected val map: AMap = mapView.getMap()

    private val markers = mutableMapOf<String, Marker>()

    override val view: View get() = mapView.view

    override fun moveTo(latitude: Double, longitude: Double, zoom: Float) =
        map.moveCamera(latitude, longitude, zoom)

    override fun addMarker(id: String, title: String, latitude: Double, longitude: Double) {
        markers[id] = map.addMarker(MarkerOptions(com.amap.api.maps.LatLng(latitude, longitude), title))
    }

    override fun removeMarker(id: String) {
        markers.remove(id)?.let { map.removeMarker(it) }
    }

    override fun clearMarkers() {
        map.clear()
        markers.clear()
    }

    /** 850 就有的图层配置：地图创建后打开路况 */
    override fun onCreate() {
        mapView.onCreate()
        map.setTrafficEnabled(true)
    }

    override fun onResume() = mapView.onResume()

    override fun onPause() = mapView.onPause()

    override fun onDestroy() = mapView.onDestroy()
}

private fun LatLng.toLatLonPoint(): LatLonPoint = LatLonPoint(latitude, longitude)
