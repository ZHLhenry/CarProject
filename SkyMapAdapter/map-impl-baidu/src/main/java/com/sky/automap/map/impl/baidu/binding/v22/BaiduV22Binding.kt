package com.sky.automap.map.impl.baidu.binding.v22

import android.content.Context
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.BaiduMapOptions
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng as BaiduLatLng
import com.baidu.mapapi.search.PoiSearch
import com.baidu.mapapi.search.RoutePlanSearch
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities
import com.sky.automap.map.impl.baidu.binding.v21.BaiduV21Binding
import com.sky.automap.map.impl.baidu.binding.v21.BaiduV21NativeMap
import com.sky.automap.map.impl.baidu.spi.BaiduNativeMap
import com.sky.automap.map.impl.baidu.spi.RawBaiduPoi

/**
 * 百度 **v22** 版本绑定：继承 v21，只覆写四处变化 + 打开新增能力位（包 `binding.v22`）。
 *
 * 标注增删、地图移动、生命周期、错误码处理全部继承，不复制。
 */
class BaiduV22Binding : BaiduV21Binding() {

    override val sdkVersion: String get() = "v22"

    override val capabilities: MapCapabilities
        get() = super.capabilities.copy(
            supports3D = true,
            supportsOfflineMap = true,
            supportsMultiRoute = true,
            supportsIndoorMap = true,
        )

    /** v22 改动一：隐私合规必须先于初始化 */
    override fun initialize(context: Context, ak: String) {
        SDKInitializer.setAgreePrivacy(true)
        SDKInitializer.initialize(context, ak)
    }

    /** v22 改动二：`MapView` 需传构造参数，3D / 路况在创建时决定 */
    override fun createNativeMap(context: Context): BaiduNativeMap =
        BaiduV21NativeMap(
            MapView(
                context,
                BaiduMapOptions(
                    enable3D = capabilities.supports3D,
                    showTraffic = capabilities.supportsTrafficLayer,
                ),
            ),
        )

    /** v22 改动三：检索同步返回，字段由 uid/addr 改为 poiId/address */
    override fun searchPoi(keyword: String, around: LatLng?): List<RawBaiduPoi> =
        PoiSearch().search(keyword = keyword, city = DEFAULT_CITY, pageSize = PAGE_SIZE).pois.map {
            RawBaiduPoi(
                id = it.poiId,
                name = it.name,
                address = it.address,
                latitude = it.latitude,
                longitude = it.longitude,
            )
        }

    /** v22 改动四：路线多策略同步返回 */
    override fun planRoutes(start: LatLng, end: LatLng): List<List<LatLng>> =
        RoutePlanSearch()
            .drivingSearchSync(start.toBaiduLatLng(), end.toBaiduLatLng(), STRATEGIES)
            .map { route -> route.map { LatLng(it.latitude, it.longitude) } }

    override fun planRoute(start: LatLng, end: LatLng): List<LatLng> =
        planRoutes(start, end).first()

    private companion object {
        const val DEFAULT_CITY = "全国"

        const val PAGE_SIZE = 10

        val STRATEGIES = listOf(0, 1, 2)
    }
}

private fun LatLng.toBaiduLatLng(): BaiduLatLng = BaiduLatLng(latitude, longitude)
