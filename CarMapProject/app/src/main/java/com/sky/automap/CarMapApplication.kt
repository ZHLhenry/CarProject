package com.sky.automap

import android.util.Log
import com.sky.automap.assemble.PlatformAssembler
import com.sky.automap.channel.ChannelConfig
import com.sky.automap.infrastructure.InfraInitializer
import com.sky.automap.map.factory.MapManager
import com.sky.mvvm.base.BaseApplication

/**
 * 壳工程 Application —— 「组装 + 注入」发生在这里：
 *
 * 1. 打印渠道信息（APP_BUILD_ID / 车型 / 芯片 / 产品 / 屏 / 地图），出问题先看这一行；
 * 2. 初始化基建层（SkyMVVM / SkyMultiStateLayout）；
 * 3. 把各 Flavor 选定的地图实现注入地图能力层；
 * 4. HMI 车型工程由 [PlatformAssembler] 按 Flavor 提供，运行时无需任何判断。
 */
class CarMapApplication : BaseApplication() {

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, ChannelConfig.summary())

        InfraInitializer.init(this)
        MapManager.install(PlatformAssembler.mapProvider())
    }

    private companion object {
        const val TAG = "CarMap"
    }
}
