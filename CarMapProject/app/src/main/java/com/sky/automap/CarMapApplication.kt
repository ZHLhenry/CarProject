package com.sky.automap

import android.util.Log
import com.sky.automap.assemble.PlatformAssembler
import com.sky.automap.car.service.CarServiceEnv
import com.sky.automap.car.service.RemoteCarBridge
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
 * 4. HMI 车型工程由 [PlatformAssembler] 按 Flavor 提供，运行时无需任何判断；
 * 5. 把渠道身份交给对外 AIDL 服务端（`:service`）——它拿不到 BuildConfig，只能靠注入。
 */
class CarMapApplication : BaseApplication() {

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, ChannelConfig.summary())

        InfraInitializer.init(this)
        MapManager.install(PlatformAssembler.mapProvider())

        // 放在最后：attach 会立刻向已注册的外部客户端广播 onServiceReady，
        // 若提前了，外部拿到「就绪」信号时基建层与地图实现还没装好。
        RemoteCarBridge.attach(
            CarServiceEnv(
                applicationId = packageName,
                carModel = ChannelConfig.carModel,
                channelId = ChannelConfig.buildId,
                mapVendor = ChannelConfig.mapVendor.name,
            )
        )
    }

    private companion object {
        const val TAG = "CarMap"
    }
}
