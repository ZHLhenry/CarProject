package com.sky.automap.map.impl.amap

import android.view.View
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapMarker
import com.sky.automap.map.impl.amap.spi.AmapNativeMap

/**
 * 高德地图视图（厂商层）——把业务语义翻译成厂商原生调用。
 *
 * 与 [com.sky.automap.map.impl.amap.spi.AmapNativeMap] 的分工：
 * - 本类：`MapMarker` → `id/title/经纬度`，`LatLng` → 两个 Double，`clear()` → `clearMarkers()`；
 * - AmapNativeMap：只负责真的去调 SDK。
 *
 * 因此各版本 binding 只需要实现「怎么调」，不用重复写一遍业务映射。
 */
internal class AmapMapView(
    private val nativeMap: AmapNativeMap,
) : IMapView {

    override val view: View get() = nativeMap.view

    override fun moveTo(position: LatLng, zoom: Float) {
        nativeMap.moveTo(position.latitude, position.longitude, zoom)
    }

    override fun addMarker(marker: MapMarker) {
        nativeMap.addMarker(
            id = marker.id,
            title = marker.title,
            latitude = marker.position.latitude,
            longitude = marker.position.longitude,
        )
    }

    override fun removeMarker(id: String) = nativeMap.removeMarker(id)

    override fun clear() = nativeMap.clearMarkers()
}
