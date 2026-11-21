package com.sky.automap.map.impl.baidu.binding.v21

import android.content.Context
import android.view.View
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng as BaiduLatLng
import com.baidu.mapapi.search.PoiSearch
import com.baidu.mapapi.search.RoutePlanSearch
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities
import com.sky.automap.map.impl.baidu.spi.BaiduBinding
import com.sky.automap.map.impl.baidu.spi.BaiduNativeMap
import com.sky.automap.map.impl.baidu.spi.RawBaiduPoi

/**
 * 百度 **v21** 版本绑定：百度侧的版本链基类（包 `binding.v21`）。
 *
 * 与高德 850 对称——完整实现 v21 的能力（异步检索、单路线、基础地图），
 * 这些代码同时服务 v22，因此不要在这里写「只有 v21 才有」的东西。
 */
open class BaiduV21Binding : BaiduBinding {

    override val sdkVersion: String get() = VERSION

    override val capabilities: MapCapabilities
        get() = MapCapabilities(
            sdkName = "baidu",
            sdkVersion = sdkVersion,
            supportsTrafficLayer = true,
        )

    override fun initialize(context: Context, ak: String) {
        SDKInitializer.initialize(context, ak)
    }

    override fun createNativeMap(context: Context): BaiduNativeMap =
        BaiduV21NativeMap(MapView(context))

    override fun searchPoi(keyword: String, around: LatLng?): List<RawBaiduPoi> {
        var result: List<RawBaiduPoi> = emptyList()
        PoiSearch().searchInCity(city = DEFAULT_CITY, keyword = keyword) { pois, code ->
            if (code == SUCCESS_CODE) {
                result = pois.orEmpty().map {
                    RawBaiduPoi(
                        id = it.uid,
                        name = it.name,
                        address = it.addr,
                        latitude = it.latitude,
                        longitude = it.longitude,
                    )
                }
            }
        }
        return result
    }

    override fun planRoute(start: LatLng, end: LatLng): List<LatLng> {
        var path: List<LatLng> = listOf(start, end)
        RoutePlanSearch().drivingSearch(start.toBaiduLatLng(), end.toBaiduLatLng()) { points, code ->
            if (code == SUCCESS_CODE) {
                path = points.orEmpty().map { LatLng(it.latitude, it.longitude) }
            }
        }
        return path
    }

    override fun release() = Unit

    private companion object {
        const val VERSION = "v21"

        const val SUCCESS_CODE = 0

        /** v21 的检索接口强制要求城市参数 */
        const val DEFAULT_CITY = "全国"
    }
}

/**
 * v21 的原生地图外观（v22 复用：标注 API 形状未变，只是构造方式变了）。
 */
open class BaiduV21NativeMap(private val mapView: MapView) : BaiduNativeMap {

    protected val map = mapView.getMap()

    override val view: View get() = mapView.view

    override fun moveTo(latitude: Double, longitude: Double, zoom: Float) =
        map.setMapStatus(latitude, longitude, zoom)

    override fun addMarker(id: String, title: String, latitude: Double, longitude: Double) =
        map.addOverlay(id, title, latitude, longitude)

    override fun removeMarker(id: String) = map.removeOverlay(id)

    override fun clearMarkers() = map.clearOverlays()

    override fun onCreate() = mapView.onCreate()

    override fun onResume() = mapView.onResume()

    override fun onPause() = mapView.onPause()

    override fun onDestroy() = mapView.onDestroy()
}

private fun LatLng.toBaiduLatLng(): BaiduLatLng = BaiduLatLng(latitude, longitude)
