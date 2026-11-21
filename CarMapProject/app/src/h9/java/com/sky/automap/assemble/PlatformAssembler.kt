package com.sky.automap.assemble

import com.sky.automap.BuildConfig
import com.sky.automap.hmi.api.HmiEntry
import com.sky.automap.hmi.h9.HmiH9Entry
import com.sky.automap.map.api.IMapProvider
import com.sky.automap.map.impl.baidu.BaiduMapProvider
import com.sky.automap.map.impl.baidu.binding.BaiduBindingFactory
import com.sky.automap.map.impl.baidu.binding.BaiduVersion

/**
 * H9 装配器 —— 只存在于车型源集 `app/src/h9`，由 carModel=H9 的**所有渠道**共用。
 *
 * 挂载方式：app/build.gradle.kts 把 `src/h9/java` 加进每个 H9 渠道 flavor 的源集，
 * 与 `app/src/h6` 里的同名对象「同包同名、编译期二选一」—— 产物里只有这一份，
 * 运行时没有任何 if/else，也不存在 H6 的代码与资源。
 *
 * 装配内容：车型 HMI（[HmiH9Entry]）+ 地图厂商（百度 `map-impl-baidu`）。
 * **SDK 版本由渠道决定，不写死在这里**（app-builds.json → BuildConfig.APP_MAP_VERSION）：
 *   ..._baiduv21 → `BaiduVersion.V21`（build.gradle.kts 打 map-sdk-baidu-v21）
 *   ..._baiduv22 → `BaiduVersion.V22`（build.gradle.kts 打 map-sdk-baidu-v22）
 *
 * 依赖注入与这里的选择必须一致；对不上时影子 SDK 的版本标定（`BaiduSdk.mode`）
 * 会在启动第一秒抛异常，不会出现「声明 v21、包里却是 v22」的静默错配。
 * 厂商层 `BaiduMapProvider` 与业务组件都不用动。
 */
object PlatformAssembler {

    /** 注入 HMI 独立工程（H9） */
    fun hmiEntry(): HmiEntry = HmiH9Entry

    /** 注入具体地图实现：厂商固定百度，版本由渠道决定 */
    fun mapProvider(): IMapProvider = BaiduMapProvider(BaiduBindingFactory.of(baiduVersion()))

    private fun baiduVersion(): BaiduVersion =
        BaiduVersion.entries.firstOrNull { it.code == BuildConfig.APP_MAP_VERSION }
            ?: throw IllegalStateException(
                "H9 渠道不支持的地图版本：${BuildConfig.APP_MAP_VERSION}" +
                    "（可选：${BaiduVersion.entries.joinToString { it.code }}）" +
                    "，检查 app-builds.json 的 carModels.h9.mapVersions"
            )
}
