package com.baidu.mapapi.map

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.baidu.mapapi.shim.ApiRemoved
import com.baidu.mapapi.shim.ApiSince
import com.baidu.mapapi.shim.BaiduSdk

/**
 * 百度地图视图（v21 / v22 并集影子层）。
 *
 * - v21：`MapView(context)` 单参构造；
 * - v22：单参构造被移除，3D / 路况改由 [BaiduMapOptions] 在构造期决定。
 */
class MapView private constructor(
    context: Context,
    options: BaiduMapOptions,
    applyOptions: Boolean,
) {

    private val map = BaiduMap().also { baiduMap ->
        if (applyOptions && BaiduSdk.currentLevel >= BaiduSdk.levelOf(BaiduSdk.V22)) {
            baiduMap.set3DEnabled(options.enable3D)
            baiduMap.setTrafficEnabled(options.showTraffic)
        }
    }

    val view: View = FrameLayout(context).apply {
        setBackgroundColor(
            Color.parseColor(
                if (BaiduSdk.currentLevel >= BaiduSdk.levelOf(BaiduSdk.V22)) "#EDE9FE" else "#E6F1FB",
            ),
        )
        addView(
            TextView(context).apply {
                text = buildString {
                    append("Baidu Map SDK ").append(BaiduSdk.mode).append(" · MapView")
                    if (BaiduSdk.currentLevel >= BaiduSdk.levelOf(BaiduSdk.V22)) append("（3D=${options.enable3D}）")
                }
                gravity = Gravity.CENTER
                setTextColor(
                    Color.parseColor(
                        if (BaiduSdk.currentLevel >= BaiduSdk.levelOf(BaiduSdk.V22)) "#5B21B6" else "#185FA5",
                    ),
                )
                textSize = 12f
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply { gravity = Gravity.CENTER },
        )
    }

    /** v22 构造方式 */
    @ApiSince(BaiduSdk.V22)
    constructor(context: Context, options: BaiduMapOptions) : this(context, options, applyOptions = true)

    /** v21 构造方式，v22 已移除 */
    @ApiRemoved(BaiduSdk.V22)
    constructor(context: Context) : this(context, BaiduMapOptions(), applyOptions = false) {
        BaiduSdk.gate(BaiduSdk.V21, BaiduSdk.V22, "MapView(Context)")
    }

    fun getMap(): BaiduMap = map

    fun onCreate() = Unit

    fun onResume() = Unit

    fun onPause() = Unit

    fun onDestroy() = Unit
}

/** v22 新增：构造期配置 */
@ApiSince(BaiduSdk.V22)
data class BaiduMapOptions(
    val enable3D: Boolean = true,
    val showTraffic: Boolean = false,
)

/**
 * 地图对象。
 *
 * 标注 API 两版形状一致（以业务 id 为键），这也是 v22 能直接复用 v21 原生地图外观的原因。
 * [onMapLoadedCallback] 两版都有但签名不同（v21 无参 / v22 带成功标志），
 * 这里保留 v22 的签名——绑定层本来就不用这个回调。
 */
class BaiduMap {

    var onMapLoadedCallback: ((success: Boolean) -> Unit)? = null

    private var trafficEnabled: Boolean = false

    private var enabled3D: Boolean = false

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

    /** v22 新增 */
    @ApiSince(BaiduSdk.V22)
    fun set3DEnabled(enabled: Boolean) {
        BaiduSdk.gate(BaiduSdk.V22, null, "BaiduMap.set3DEnabled")
        enabled3D = enabled
    }

    /** v22 新增 */
    @ApiSince(BaiduSdk.V22)
    fun is3DEnabled(): Boolean = enabled3D

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
