plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 高德 **850** 影子 SDK（对应一份真实 aar）。
 *
 * API 面 = 基础能力 + 路况图层 + 地图类型 + 检索分页；构造仍是 `MapView(context)`。
 * 壳工程按车型注入（`h6Implementation("com.sky.automap:map-sdk-amap-850")`），
 * 真实接入时换成官方坐标 `com.amap.api:3dmap:8.5.0`。
 *
 * 不参与 `map-impl-amap` 的编译：编译基线是 `map-sdk-amap-910`（三版 API 并集）。
 */
android {
    namespace = "com.sky.automap.map.sdk.amap.v850"

    publishing {
        singleVariant("release")
    }
}
