plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 百度 **v22** 影子 SDK（对应一份真实 aar），同时是 `map-impl-baidu` 的**编译基线**。
 *
 * 为什么最新版模块要兼任基线：
 * 适配代码（v21 / v22 两段）同处 `map-impl-baidu` 一个模块，只有一条编译 classpath。
 * v21 段调用的 `MapView(context)`、`searchInCity`、`PoiInfo.uid` 在 v22 已被移除，
 * 要让这两段同时编译通过，classpath 上必须有一份「两版 API 的并集」。
 * 与其再加一个只用于编译的第 3 个模块，不如放在最新版模块里：
 * - 并集天然属于最新版（新版本 = 老版本 + 新增 - 移除）；
 * - 那些「只该在老版本存在」的成员保留下来，但都带 `@ApiRemoved`，
 *   运行期调用即抛（`BaiduSdk.gate`），语义与真机一致，只是错误信息更清楚；
 * - `verifyMapSdkChain` 正是读这份注解来判断「哪段代码必须被新版本绑定覆写」。
 *
 * 壳工程按车型注入（`h9Implementation("com.sky.automap:map-sdk-baidu-v22")`）。
 * 真实接入官方 aar 时：本模块删除，`map-impl-baidu` 改
 * `compileOnly("com.baidu.lbsapi:…")`，壳工程注入官方坐标。
 */
android {
    namespace = "com.sky.automap.map.sdk.baidu.v22"

    publishing {
        singleVariant("release")
    }
}
