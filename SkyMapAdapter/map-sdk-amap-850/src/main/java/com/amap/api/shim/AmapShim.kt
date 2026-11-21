package com.amap.api.shim

/**
 * 高德 **850** 影子 SDK 的版本标定。
 *
 * 850 的 API 面 = 基础能力 + 路况图层 + 地图类型 + 检索分页，构造方式仍是 `MapView(context)`。
 * 它没有 910 的 `AMapOptions`、隐私合规、同步检索，也没有 910 才有的 3D。
 *
 * [mode] 只有一个合法值，见 [AmapSdk.mode]。
 */
object AmapSdk {

    /** 本模块模拟的 SDK 版本 */
    const val MODULE_VERSION: String = "850"

    @Volatile
    var mode: String = MODULE_VERSION
        set(value) {
            require(value == MODULE_VERSION) {
                "本模块是 AMap $MODULE_VERSION 影子 SDK，却被要求切换成 $value。" +
                    "版本由壳工程注入的 AAR 决定，请让 AmapBindingFactory.of(...) 与车型矩阵里的 sdk 保持一致。"
            }
            field = value
        }
}
