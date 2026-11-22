package com.sky.automap.car.service

import com.sky.automap.car.api.model.CarNavState
import com.sky.automap.car.api.model.CarPoi

/**
 * 导航状态（进程级单例）。
 *
 * 当前是**模拟态**：`map-api` 的 `IMapProvider` 只提供了 searchPoi / planRoute，
 * 还没有起终点导航能力，所以这里用固定里程占位，先把
 * 「状态怎么持有 → 怎么对外返回 → 变化时怎么推送」这条链路打通。
 *
 * 接真实导航时的改动范围（契约与外部调用方都不用动）：
 * 1. `start()` 换成 SDK 的 `startNavi(poi)`；
 * 2. `stop()` 换成 `stopNavi()`；
 * 3. 在 SDK 的进度回调里构造新的 [CarNavState] 并调用
 *    [RemoteCarBridge.broadcastNavState]。
 */
internal object NavStateHolder {

    @Volatile
    private var state: CarNavState = CarNavState.IDLE

    fun current(): CarNavState = state

    fun start(destination: CarPoi): CarNavState {
        val next = CarNavState(
            navigating = true,
            destinationName = destination.name,
            // 占位值：真实里程/ETA 必须来自 SDK 的导航回调，不要在这里做估算
            remainingDistanceMeters = 1200,
            etaSeconds = 300,
        )
        state = next
        return next
    }

    fun stop(): CarNavState {
        state = CarNavState.IDLE
        return state
    }
}
