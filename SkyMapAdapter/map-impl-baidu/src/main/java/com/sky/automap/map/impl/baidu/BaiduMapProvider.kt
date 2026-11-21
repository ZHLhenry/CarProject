package com.sky.automap.map.impl.baidu

import android.content.Context
import com.sky.automap.map.api.IMapProvider
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities
import com.sky.automap.map.api.MapPoi
import com.sky.automap.map.api.MapSdkType
import com.sky.automap.map.impl.baidu.spi.BaiduBinding

/**
 * 百度地图实现（impl-baidu.aar）——**厂商公共层**。
 *
 * 不出现任何 `com.baidu.mapapi.*` 的 import；版本差异（v21 / v22）由
 * `binding.v21` / `binding.v22` 两个包承担。
 */
class BaiduMapProvider(
    private val binding: BaiduBinding,
    private val ak: String = DEFAULT_AK,
) : IMapProvider {

    override val sdkType: MapSdkType = MapSdkType.BAIDU

    override val sdkVersion: String get() = binding.sdkVersion

    override val capabilities: MapCapabilities get() = binding.capabilities

    @Volatile
    private var initialized = false

    override fun createMapView(context: Context): IMapView {
        ensureInitialized(context)
        val nativeMap = binding.createNativeMap(context).also { it.onCreate() }
        return BaiduMapView(nativeMap)
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

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            binding.initialize(context.applicationContext, ak)
            initialized = true
        }
    }

    companion object {
        /** 演示用 AK，真实接入时由壳工程按车型下发 */
        const val DEFAULT_AK: String = "baidu-demo-ak"
    }
}
