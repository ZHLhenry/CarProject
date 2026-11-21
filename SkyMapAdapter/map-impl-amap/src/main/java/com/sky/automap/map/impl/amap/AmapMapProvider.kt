package com.sky.automap.map.impl.amap

import android.content.Context
import com.sky.automap.map.api.IMapProvider
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities
import com.sky.automap.map.api.MapPoi
import com.sky.automap.map.api.MapSdkType
import com.sky.automap.map.impl.amap.spi.AmapBinding

/**
 * 高德地图实现（impl-amap.aar）——**厂商公共层**。
 *
 * 本类不出现任何 `com.amap.api.*` 的 import，只做三件事：
 * 1. 把 [IMapProvider] 的调用转发给当前绑定的 [AmapBinding]；
 * 2. 把厂商原始数据归一为契约数据模型（`RawPoi` → `MapPoi`）；
 * 3. 承载与 SDK 版本无关的公共逻辑（懒初始化、错误码、埋点、降级策略）。
 *
 * 版本差异全部落在 `binding.v850` / `binding.v910` 两个包，
 * 因此新增一个高德版本时，本模块与上游业务组件**一行都不用改**。
 *
 * @param binding 由壳工程按车型注入的版本绑定（H6 → 850，H9 → 910）
 */
class AmapMapProvider(
    private val binding: AmapBinding,
    private val apiKey: String = DEFAULT_API_KEY,
) : IMapProvider {

    override val sdkType: MapSdkType = MapSdkType.AMAP

    override val sdkVersion: String get() = binding.sdkVersion

    override val capabilities: MapCapabilities get() = binding.capabilities

    @Volatile
    private var initialized = false

    override fun createMapView(context: Context): IMapView {
        ensureInitialized(context)
        val nativeMap = binding.createNativeMap(context).also { it.onCreate() }
        return AmapMapView(nativeMap)
    }

    override fun searchPoi(keyword: String, around: LatLng?): List<MapPoi> =
        binding.searchPoi(keyword, around).map { raw ->
            MapPoi(
                id = raw.id,
                name = raw.name,
                address = raw.address,
                position = LatLng(raw.latitude, raw.longitude),
            )
        }

    override fun planRoute(start: LatLng, end: LatLng): List<LatLng> =
        binding.planRoute(start, end)

    override fun planRoutes(start: LatLng, end: LatLng): List<List<LatLng>> =
        binding.planRoutes(start, end)

    /** SDK 初始化各版本差异较大，统一交给 binding，本层只保证「只初始化一次」 */
    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            binding.initialize(context.applicationContext, apiKey)
            initialized = true
        }
    }

    companion object {
        /** 演示用 Key，真实接入时由壳工程按车型下发 */
        const val DEFAULT_API_KEY: String = "amap-demo-key"
    }
}
