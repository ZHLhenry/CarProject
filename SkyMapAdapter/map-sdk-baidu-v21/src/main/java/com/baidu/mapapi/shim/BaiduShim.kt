package com.baidu.mapapi.shim

/**
 * 百度 **v21** 影子 SDK 的版本标定。
 *
 * v21 的 API 面：`MapView(context)` 单参构造、`PoiInfo.uid/addr`、
 * `searchInCity` 异步检索、`drivingSearch` 回调单条。
 * v22 才把构造改成 `BaiduMapOptions`、字段改成 `poiId/address`、检索与路线改同步。
 *
 * [mode] 只有一个合法值：它与壳工程注入的 AAR 必须一致，否则启动即失败。
 */
object BaiduSdk {

    /** 本模块模拟的 SDK 版本 */
    const val MODULE_VERSION: String = "v21"

    @Volatile
    var mode: String = MODULE_VERSION
        set(value) {
            require(value == MODULE_VERSION) {
                "本模块是百度地图 $MODULE_VERSION 影子 SDK，却被要求切换成 $value。" +
                    "版本由壳工程注入的 AAR 决定，请让 BaiduBindingFactory.of(...) 与车型矩阵里的 sdk 保持一致。"
            }
            field = value
        }
}
