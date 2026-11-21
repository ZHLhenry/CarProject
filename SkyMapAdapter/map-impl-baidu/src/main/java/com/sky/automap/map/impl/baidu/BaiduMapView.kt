package com.sky.automap.map.impl.baidu

import android.view.View
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapMarker
import com.sky.automap.map.impl.baidu.spi.BaiduNativeMap

/** 百度地图视图（厂商层）：业务语义 → 原生调用的翻译，与版本无关 */
internal class BaiduMapView(
    private val nativeMap: BaiduNativeMap,
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
