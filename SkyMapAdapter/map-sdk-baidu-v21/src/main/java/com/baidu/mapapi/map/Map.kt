package com.baidu.mapapi.map

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.baidu.mapapi.shim.BaiduSdk

/**
 * 百度 v21 影子 SDK：地图部分。
 *
 * - [MapView] 只有 `MapView(context)` 单参构造（v22 换成 `MapView(context, BaiduMapOptions)`）；
 * - [BaiduMap] 没有 3D（v22 才加），路况图层 v21 就有；
 * - 没有 `BaiduMapOptions` 这个类。
 */
class MapView(context: Context) {

    private val map = BaiduMap()

    val view: View = FrameLayout(context).apply {
        setBackgroundColor(Color.parseColor("#E6F1FB"))
        addView(
            TextView(context).apply {
                text = "Baidu Map SDK ${BaiduSdk.mode} · MapView"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#185FA5"))
                textSize = 12f
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply { gravity = Gravity.CENTER },
        )
    }

    fun getMap(): BaiduMap = map

    fun onCreate() = Unit

    fun onResume() = Unit

    fun onPause() = Unit

    fun onDestroy() = Unit
}

/** 地图对象：v21 的形状（v22 复用同一套标注 API，只是构造方式变了） */
class BaiduMap {

    /** v21 的加载回调无参；v22 换成带成功标志的 `onMapLoadedCallback` */
    var onMapLoadedListener: (() -> Unit)? = null

    private var trafficEnabled: Boolean = false

    private var status: Triple<Double, Double, Float> = Triple(0.0, 0.0, 15f)

    private val overlays = linkedMapOf<String, BaiduOverlay>()

    fun setMapStatus(latitude: Double, longitude: Double, zoom: Float) {
        status = Triple(latitude, longitude, zoom)
    }

    fun mapStatus(): Triple<Double, Double, Float> = status

    fun setTrafficEnabled(enabled: Boolean) {
        trafficEnabled = enabled
    }

    fun isTrafficEnabled(): Boolean = trafficEnabled

    fun addOverlay(id: String, title: String, latitude: Double, longitude: Double) {
        overlays[id] = BaiduOverlay(id, title, latitude, longitude)
    }

    fun removeOverlay(id: String) {
        overlays.remove(id)
    }

    fun clearOverlays() {
        overlays.clear()
    }

    fun overlayCount(): Int = overlays.size
}

data class BaiduOverlay(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
)
