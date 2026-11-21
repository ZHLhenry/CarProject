package com.sky.automap.infrastructure

/**
 * 基建层全局常量。
 *
 * Infrastructure 是全工程唯一的「基础建设」出口：上层（业务组件层 / HMI 层 / 壳工程）
 * 只依赖本模块，不直接依赖 SkyMVVM / SkyWidget / SkyMultiStateLayout 的具体坐标，
 * 便于基础框架版本统一升级。
 */
object InfraConfig {

    /** 壳工程基准包名（车型包名在此基础上追加车型后缀） */
    const val BASE_PACKAGE: String = "com.sky.automap"

    /** 日志统一 TAG 前缀 */
    const val LOG_TAG: String = "CarMap"

    /** 地图默认缩放级别 */
    const val DEFAULT_MAP_ZOOM: Float = 15f
}
