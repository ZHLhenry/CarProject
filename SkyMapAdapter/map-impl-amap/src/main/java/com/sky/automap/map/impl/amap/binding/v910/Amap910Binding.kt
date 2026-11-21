package com.sky.automap.map.impl.amap.binding.v910

import android.content.Context
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.LatLonPoint
import com.amap.api.services.PoiSearch
import com.amap.api.services.RouteSearch
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities
import com.sky.automap.map.impl.amap.binding.v850.Amap850NativeMap
import com.sky.automap.map.impl.amap.binding.v850.Amap850Binding
import com.sky.automap.map.impl.amap.spi.AmapNativeMap
import com.sky.automap.map.impl.amap.spi.RawPoi

/**
 * 高德 **910** 版本绑定：继承 850，只覆写「被改掉的 API」与「新增能力」（包 `binding.v910`）。
 *
 * 覆写的每个方法都对应 910 的一处破坏性变更，注释里写清了原因；
 * 其余能力（标注增删、地图移动、生命周期、错误码、坐标映射）全部由 850 提供。
 *
 * 对比一下工作量：如果按「每个版本把 850 全部功能复制一遍」的写法，
 * 这个类会有 150 行以上重复代码，而且 850 修 bug 时两份都要改。
 */
class Amap910Binding : Amap850Binding() {

    override val sdkVersion: String get() = "910"

    override val capabilities: MapCapabilities
        get() = super.capabilities.copy(
            supports3D = true,
            supportsOfflineMap = true,
            supportsMultiRoute = true,
            supportsIndoorMap = true,
        )

    /** 910 改动一：隐私合规必须早于初始化，父类的「只 setApiKey 再 initialize」在 910 会抛异常 */
    override fun initialize(context: Context, apiKey: String) {
        MapsInitializer.updatePrivacyShow(context, contains = true, agreed = true)
        MapsInitializer.setPrivacyAgree(true)
        MapsInitializer.setApiKey(apiKey)
        MapsInitializer.initialize(context)
    }

    /** 910 改动二：`MapView` 单参构造被移除，3D / 路况改由构造期 `AMapOptions` 决定 */
    override fun createNativeMap(context: Context): AmapNativeMap =
        Amap850NativeMap(
            MapView(
                context,
                AMapOptions(
                    mapType3D = capabilities.supports3D,
                    showTraffic = capabilities.supportsTrafficLayer,
                ),
            ),
        )

    /** 910 改动三：检索由异步回调改为同步返回，`PoiItem` 字段由 uid/title/snippet 改成 poiId/name/address */
    override fun searchPoi(keyword: String, around: LatLng?): List<RawPoi> =
        PoiSearch().search(keyword, around?.toLatLonPoint(), pageSize = PAGE_SIZE).pois.map {
            RawPoi(
                id = it.poiId,
                name = it.name,
                address = it.address,
                latitude = it.point.latitude,
                longitude = it.point.longitude,
            )
        }

    /** 910 改动四：路线改为多策略同步返回，回调版 `calculateDriveRoute` 已移除 */
    override fun planRoutes(start: LatLng, end: LatLng): List<List<LatLng>> =
        RouteSearch()
            .calculateDriveRouteSync(start.toLatLonPoint(), end.toLatLonPoint(), STRATEGIES)
            .map { route -> route.points.map { LatLng(it.latitude, it.longitude) } }

    override fun planRoute(start: LatLng, end: LatLng): List<LatLng> =
        planRoutes(start, end).first()

    private companion object {
        const val PAGE_SIZE = 10

        /** 0 速度优先 / 1 费用优先 / 2 距离优先 */
        val STRATEGIES = listOf(0, 1, 2)
    }
}

private fun LatLng.toLatLonPoint(): LatLonPoint = LatLonPoint(latitude, longitude)
