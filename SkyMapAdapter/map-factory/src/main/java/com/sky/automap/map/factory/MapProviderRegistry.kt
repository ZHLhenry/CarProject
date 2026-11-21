package com.sky.automap.map.factory

import com.sky.automap.map.api.IMapProvider

/**
 * 地图实现注册表（map-factory 内部实现，`internal` 不对外暴露）。
 *
 * 只在 factory 内部的 [MapManager] 中使用：
 * - 壳工程通过 [MapManager.install] 写入实现；
 * - 业务组件通过 [MapManager] 的语义化方法取用能力。
 *
 * 因为不导出到 `map-api` 契约层，接口层保持「零可变状态」，
 * 未来要换成 Hilt / ServiceLoader 注入时，只改本文件与 [MapManager]，`map-api` 二进制不变。
 */
internal object MapProviderRegistry {

    @Volatile
    private var provider: IMapProvider? = null

    /** 注入具体地图实现（重复注入以最后一次为准） */
    fun install(provider: IMapProvider) {
        this.provider = provider
    }

    /** 是否已注入实现 */
    fun isInstalled(): Boolean = provider != null

    /** 获取当前实现，未注入时抛出明确异常，便于排查装配遗漏 */
    fun current(): IMapProvider = provider
        ?: error("地图实现尚未注入：请在壳工程 Application 中调用 MapManager.install(IMapProvider)")
}
