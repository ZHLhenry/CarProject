plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 百度 **v21** 影子 SDK（对应一份真实 aar）。
 *
 * API 面：`MapView(context)`、`PoiInfo.uid/addr`、`searchInCity`、`drivingSearch`。
 * 壳工程按车型注入（`h9Implementation("com.sky.automap:map-sdk-baidu-v21")`），
 * 真实接入时换成官方坐标 `com.baidu.lbsapi:…`。
 *
 * 不参与 `map-impl-baidu` 的编译：编译基线是 `map-sdk-baidu-v22`（两版 API 并集）。
 */
android {
    namespace = "com.sky.automap.map.sdk.baidu.v21"

    publishing {
        singleVariant("release")
    }
}
