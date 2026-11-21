package com.amap.api.maps

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.amap.api.shim.AmapSdk

/**
 * 高德 850 影子 SDK：地图部分（对应真实 aar 的 `com.amap.api.maps`）。
 *
 * 本模块相对更早版本的增量只有三处，正是 850 真实的变化（650 已不再支持）：
 * - [AMap.setTrafficEnabled] / [AMap.isTrafficEnabled]：路况图层；
 * - [AMap.setMapType] / [AMap.getMapType]：地图类型；
 * - [AMap.MAP_TYPE_NORMAL] / [AMap.MAP_TYPE_SATELLITE]：对应常量。
 *
 * 构造方式仍是 `MapView(context)`；隐私合规（910）、`AMapOptions`（910）、3D（910）一概没有。
 */
object MapsInitializer {

    private var apiKey: String = ""

    @Volatile
    private var ready: Boolean = false

    fun setApiKey(key: String) {
        apiKey = key
    }

    fun initialize(context: Context): Boolean {
        ready = apiKey.isNotBlank()
        Log.i("AMapShim", "AMap ${AmapSdk.mode} initialize -> ready=$ready")
        return ready
    }

    fun isReady(): Boolean = ready
}

/** 地图视图：850 仍然只有 `MapView(context)` 单参构造 */
class MapView(context: Context) {

    private val map = AMap()

    val view: View = FrameLayout(context).apply {
        setBackgroundColor(Color.parseColor("#E8F0FE"))
        addView(
            TextView(context).apply {
                text = "AMap SDK ${AmapSdk.mode} · MapView"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#1A56DB"))
                textSize = 12f
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply { gravity = Gravity.CENTER },
        )
    }

    fun getMap(): AMap = map

    fun onCreate() = Unit

    fun onResume() = Unit

    fun onPause() = Unit

    fun onDestroy() = Unit
}

/** 地图对象：基础能力 + 850 的路况 / 地图类型 */
class AMap {

    var onMapLoadedListener: (() -> Unit)? = null

    private val markers = linkedMapOf<String, Marker>()

    private var camera: Triple<Double, Double, Float> = Triple(0.0, 0.0, 15f)

    private var trafficEnabled: Boolean = false

    private var mapType: Int = MAP_TYPE_NORMAL

    fun moveCamera(latitude: Double, longitude: Double, zoom: Float) {
        camera = Triple(latitude, longitude, zoom)
    }

    fun cameraState(): Triple<Double, Double, Float> = camera

    fun addMarker(options: MarkerOptions): Marker {
        val marker = Marker("amap${AmapSdk.mode}-marker-${markers.size}", options)
        markers[marker.id] = marker
        return marker
    }

    fun removeMarker(marker: Marker) {
        markers.remove(marker.id)
    }

    fun clear() {
        markers.clear()
    }

    fun markerCount(): Int = markers.size

    // ---- 850 新增：路况图层 ----

    fun setTrafficEnabled(enabled: Boolean) {
        trafficEnabled = enabled
    }

    fun isTrafficEnabled(): Boolean = trafficEnabled

    // ---- 850 新增：地图类型（910 换成构造期 AMapOptions） ----

    fun setMapType(type: Int) {
        mapType = type
    }

    fun getMapType(): Int = mapType

    companion object {
        const val MAP_TYPE_NORMAL: Int = 1

        const val MAP_TYPE_SATELLITE: Int = 2
    }
}

data class LatLng(val latitude: Double, val longitude: Double)

data class MarkerOptions(val position: LatLng, val title: String)

data class Marker(val id: String, val options: MarkerOptions)
