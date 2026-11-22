package com.sky.automap.car.service

/**
 * 服务端运行期身份，由壳工程在 `Application.onCreate` 里注入。
 *
 * 为什么要注入、而不是 service 直接读 `BuildConfig`：
 * 1. 渠道参数（APP_CAR_MODEL / APP_BUILD_ID / APP_MAP_VENDOR）是 `:app` 按 flavor
 *    生成的 BuildConfig 字段，库模块引用不到；
 * 2. 若让 `:service` 反过来依赖 `:app`，依赖方向就倒挂了（库依赖宿主），
 *    而且 `:service` 再也无法单独编译。
 *
 * 于是「谁知道渠道信息」和「谁实现 Binder」分开：`:app` 知道但不说，
 * `:service` 说但不知道，两者靠这个纯数据类对接。
 */
data class CarServiceEnv(
    /** 实际包名：按车型覆盖为 com.sky.automap.h6 / .h9 */
    val applicationId: String,
    /** 车型：H6 / H9 */
    val carModel: String,
    /** 渠道 ID（app-builds.json 的键），同车型多渠道时用于区分 */
    val channelId: String,
    /** 地图厂商：AMAP / BAIDU */
    val mapVendor: String,
)
