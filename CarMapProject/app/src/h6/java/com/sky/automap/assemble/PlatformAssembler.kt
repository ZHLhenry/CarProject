package com.sky.automap.assemble

import com.sky.automap.BuildConfig
import com.sky.automap.hmi.api.HmiEntry
import com.sky.automap.hmi.h6.HmiH6Entry
import com.sky.automap.map.api.IMapProvider
import com.sky.automap.map.impl.amap.AmapMapProvider
import com.sky.automap.map.impl.amap.binding.AmapBindingFactory
import com.sky.automap.map.impl.amap.binding.AmapVersion

/**
 * H6 装配器 —— 只存在于车型源集 `app/src/h6`，由 carModel=H6 的**所有渠道**共用。
 *
 * 挂载方式：app/build.gradle.kts 把 `src/h6/java` 加进每个 H6 渠道 flavor 的源集，
 * 与 `app/src/h9` 里的同名对象「同包同名、编译期二选一」—— 产物里只有这一份，
 * 运行时没有任何 if/else，也不存在 H9 的代码与资源。
 *
 * 装配内容：车型 HMI（[HmiH6Entry]）+ 地图厂商（高德 `map-impl-amap`）。
 * **SDK 版本由渠道决定，不写死在这里**（app-builds.json → BuildConfig.APP_MAP_VERSION）：
 *   ..._amap850 → `AmapVersion.V850`（build.gradle.kts 打 map-sdk-amap-850）
 *   ..._amap910 → `AmapVersion.V910`（build.gradle.kts 打 map-sdk-amap-910）
 *
 * 依赖注入与这里的选择必须一致；对不上时影子 SDK 的版本标定（`AmapSdk.mode`）
 * 会在启动第一秒抛异常，不会出现「声明 850、包里却是 910」的静默错配。
 */
object PlatformAssembler {

    /** 注入 HMI 独立工程（H6） */
    fun hmiEntry(): HmiEntry = HmiH6Entry

    /** 注入具体地图实现：厂商固定高德，版本由渠道决定 */
    fun mapProvider(): IMapProvider = AmapMapProvider(AmapBindingFactory.of(amapVersion()))

    private fun amapVersion(): AmapVersion =
        AmapVersion.entries.firstOrNull { it.code == BuildConfig.APP_MAP_VERSION }
            ?: throw IllegalStateException(
                "H6 渠道不支持的地图版本：${BuildConfig.APP_MAP_VERSION}" +
                    "（可选：${AmapVersion.entries.joinToString { it.code }}）" +
                    "，检查 app-builds.json 的 carModels.h6.mapVersions"
            )
}
