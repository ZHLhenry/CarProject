package com.sky.automap.hmi.api

/**
 * HMI 车型工程对外契约。
 *
 * 每个车型 HMI（H6 / H9）是独立的「UI 视觉 + 车型专属交互」工程，
 * 壳工程在打包时按 Flavor 选择性组装其中一个，因此上层只能依赖本契约，
 * 不能直接依赖具体的 HMI 实现模块。
 */
interface HmiEntry {

    /** 车型标识，如 H6 / H9 */
    val modelName: String

    /** 车型 HMI 展示名称 */
    val displayName: String

    /** 车型主题色（HMI 视觉差异化的最小体现） */
    val themeColor: Int

    /** 该车型 HMI 的导航图资源 id（单 Activity + 多 Fragment 的入口图） */
    fun navGraphId(): Int
}
