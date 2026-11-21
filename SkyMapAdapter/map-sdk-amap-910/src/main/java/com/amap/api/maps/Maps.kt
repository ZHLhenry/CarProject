package com.amap.api.maps

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.amap.api.shim.AmapSdk
import com.amap.api.shim.ApiRemoved
import com.amap.api.shim.ApiSince

/**
 * 高德 **910** 影子 SDK / 编译基线（`map-sdk-amap-910`）。
 *
 * ## 一个模块两种角色
 *
 * 1. **910 的 API 面**：`MapView(context, AMapOptions)`、隐私合规、同步检索、多策略路线。
 *    H6 若切到 910 链条，打进包的就是本模块。
 * 2. **编译基线**：`map-impl-amap` 里 850 / 910 三段适配共用一条 classpath，
 *    要同时编译通过，classpath 上必须是「三版 API 的并集」。并集天然属于最新版
 *    （新版本 = 老版本 + 新增 − 移除），所以放在这里，不再单独加一个只用于编译的模块。
 *
 * 于是本模块里会看到两类成员：
 * - 910 新增：`MapView(context, AMapOptions)`、[AMapOptions]、`PoiSearch.search`；
 * - 已被 910 移除、仅为让 850 段能编译而保留：`MapView(context)`、`searchPOIAsyn`、
 *   `PoiItem.uid`……它们都带 [ApiRemoved]，被调用时 [AmapSdk.gate] 直接抛异常，
 *   语义与真机一致（真机是 `NoSuchMethodError`），只是错误信息能直接点出符号名。
 *
 * 编译器替你区分不了「这行在 910 上能不能跑」，所以还有两道闸：
 * 1. [AmapSdk.gate]：运行期第一秒抛（漏覆写立刻崩）；
 * 2. `./gradlew verifyMapSdkChain`：CI 里静态检查（构建期拦，先于运行期）。
 *
 * `map-sdk-amap-850` 是干净的单版本 API 面，没有这些历史成员。
 *
 * ## 与真实 aar 的关系
 *
 * 接官方 SDK 时整份模块替换：`compileOnly("com.amap.api:3dmap:…")`，
 * 真实 aar 里没有 `com.amap.api.shim` 包，也没有运行期开关——
 * 那时「每版本一份 aar」由壳工程按车型注入，`gate` 机制自然消失，
 * 静态校验任务改为对比真实 aar 的 API 面。
 */
object MapsInitializer {

    private var apiKey: String = ""

    @Volatile
    private var privacyAgreed: Boolean = false

    @Volatile
    private var ready: Boolean = false

    @ApiSince(AmapSdk.V850)
    fun setApiKey(key: String) {
        apiKey = key
    }

    /** 910 新增：隐私合规声明（`setPrivacyAgree` 与 `updatePrivacyShow` 二选一即可） */
    @ApiSince(AmapSdk.V910)
    fun setPrivacyAgree(agree: Boolean) {
        AmapSdk.gate(AmapSdk.V910, null, "MapsInitializer.setPrivacyAgree")
        privacyAgreed = agree
    }

    /** 910 新增：声明「已展示隐私政策且用户同意」 */
    @ApiSince(AmapSdk.V910)
    fun updatePrivacyShow(context: Context, contains: Boolean, agreed: Boolean) {
        AmapSdk.gate(AmapSdk.V910, null, "MapsInitializer.updatePrivacyShow")
        privacyAgreed = agreed
    }

    /**
     * 初始化。签名三个版本一致，但 910 多了**语义要求**：必须先声明隐私合规。
     *
     * 这类「签名没变、语义变了」的改动靠 API 面 diff 抓不到，
     * 只能在 binding 里覆写并注释说明——`Amap910Binding.initialize` 就是一例。
     */
    @ApiSince(AmapSdk.V850)
    fun initialize(context: Context): Boolean {
        if (AmapSdk.currentLevel >= AmapSdk.levelOf(AmapSdk.V910)) {
            check(privacyAgreed) {
                "[AMap 910] 未完成隐私合规声明，必须先 updatePrivacyShow / setPrivacyAgree 再初始化"
            }
        }
        ready = apiKey.isNotBlank()
        Log.i("AMapShim", "AMap ${AmapSdk.mode} initialize -> ready=$ready")
        return ready
    }

    fun isReady(): Boolean = ready
}

/** 910 新增：构造期配置对象 */
@ApiSince(AmapSdk.V910)
data class AMapOptions(
    val mapType3D: Boolean = true,
    val showTraffic: Boolean = false,
)

/**
 * 地图视图。
 *
 * - 850：`MapView(context)` 单参构造；
 * - 910：单参构造被移除，3D / 路况改由 [AMapOptions] 在构造期决定。
 */
class MapView private constructor(
    context: Context,
    options: AMapOptions,
    applyOptions: Boolean,
) {

    private val map = AMap().also { amap ->
        if (applyOptions && AmapSdk.currentLevel >= AmapSdk.levelOf(AmapSdk.V910)) {
            amap.set3DEnabled(options.mapType3D)
            amap.setTrafficEnabled(options.showTraffic)
        }
    }

    val view: View = placeholder(
        context = context,
        label = buildString {
            append("AMap SDK ").append(AmapSdk.mode).append(" · MapView")
            if (AmapSdk.currentLevel >= AmapSdk.levelOf(AmapSdk.V910)) append("（3D=${options.mapType3D}）")
        },
        background = if (AmapSdk.currentLevel >= AmapSdk.levelOf(AmapSdk.V910)) "#E1F5EE" else "#E8F0FE",
        foreground = if (AmapSdk.currentLevel >= AmapSdk.levelOf(AmapSdk.V910)) "#0F6E56" else "#1A56DB",
    )

    /** 910 构造方式 */
    @ApiSince(AmapSdk.V910)
    constructor(context: Context, options: AMapOptions) : this(context, options, applyOptions = true)

    /** 850 构造方式，910 已移除 */
    @ApiRemoved(AmapSdk.V910)
    constructor(context: Context) : this(context, AMapOptions(), applyOptions = false) {
        AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "MapView(Context)")
    }

    fun getMap(): AMap = map

    fun onCreate() = Unit

    fun onResume() = Unit

    fun onPause() = Unit

    fun onDestroy() = Unit
}

/** 地图对象：标注、移动、清空三个版本的形状一致 */
class AMap {

    /** 850 的加载回调（无参）；910 换成了带成功标志的 [onMapLoadedCallback] */
    @ApiRemoved(AmapSdk.V910)
    var onMapLoadedListener: (() -> Unit)? = null

    /** 910 的加载回调（带成功标志） */
    @ApiSince(AmapSdk.V910)
    var onMapLoadedCallback: ((success: Boolean) -> Unit)? = null

    private val markers = linkedMapOf<String, Marker>()

    private var camera: Triple<Double, Double, Float> = Triple(0.0, 0.0, 15f)

    private var trafficEnabled: Boolean = false

    private var mapType: Int = MAP_TYPE_NORMAL

    private var enabled3D: Boolean = false

    fun moveCamera(latitude: Double, longitude: Double, zoom: Float) {
        camera = Triple(latitude, longitude, zoom)
    }

    fun cameraState(): Triple<Double, Double, Float> = camera

    /** 850 起就有：标注需要传 [MarkerOptions]，删除需要传 [Marker] 实例 */
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

    // ---- 850 新增能力（纯增量，基类不用动） ----

    @ApiSince(AmapSdk.V850)
    fun setTrafficEnabled(enabled: Boolean) {
        AmapSdk.gate(AmapSdk.V850, null, "AMap.setTrafficEnabled")
        trafficEnabled = enabled
    }

    @ApiSince(AmapSdk.V850)
    fun isTrafficEnabled(): Boolean = trafficEnabled

    @ApiSince(AmapSdk.V850)
    @ApiRemoved(AmapSdk.V910)
    fun setMapType(type: Int) {
        AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "AMap.setMapType")
        mapType = type
    }

    @ApiSince(AmapSdk.V850)
    @ApiRemoved(AmapSdk.V910)
    fun getMapType(): Int {
        AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "AMap.getMapType")
        return mapType
    }

    // ---- 910 新增能力 ----

    @ApiSince(AmapSdk.V910)
    fun set3DEnabled(enabled: Boolean) {
        AmapSdk.gate(AmapSdk.V910, null, "AMap.set3DEnabled")
        enabled3D = enabled
    }

    @ApiSince(AmapSdk.V910)
    fun is3DEnabled(): Boolean = enabled3D

    companion object {
        const val MAP_TYPE_NORMAL: Int = 1

        const val MAP_TYPE_SATELLITE: Int = 2
    }
}

data class LatLng(val latitude: Double, val longitude: Double)

data class MarkerOptions(val position: LatLng, val title: String)

data class Marker(val id: String, val options: MarkerOptions)

internal fun placeholder(context: Context, label: String, background: String, foreground: String): View =
    FrameLayout(context).apply {
        setBackgroundColor(Color.parseColor(background))
        addView(
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(foreground))
                textSize = 12f
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply { gravity = Gravity.CENTER },
        )
    }
