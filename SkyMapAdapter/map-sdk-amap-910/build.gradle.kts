plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 高德 **910** 影子 SDK（对应一份真实 aar），同时是 `map-impl-amap` 的**编译基线**。
 *
 * 为什么最新版模块要兼任基线：
 * 适配代码（850 / 910 两段）同处 `map-impl-amap` 一个模块，只有一条编译 classpath。
 * 850 段调用的 `MapView(context)`、`searchPOIAsyn`、`PoiItem.uid` 在 910 已被移除，
 * 要让两段同时编译通过，classpath 上必须有一份「两版 API 的并集」。
 * 与其再加一个只用于编译的第 4 个模块，不如放在最新版模块里：
 * - 并集天然属于最新版（新版本 = 老版本 + 新增 - 移除）；
 * - 那些「只该在老版本存在」的成员保留下来，但都带 `@ApiRemoved`，
 *   运行期调用即抛（`AmapSdk.gate`），语义与真机一致，只是错误信息更清楚；
 * - `verifyMapSdkChain` 正是读这份注解来判断「哪段代码必须被新版本绑定覆写」。
 *
 * 因此本模块的角色是两件事：
 * 1. 850 车型**不进**它，进的是 `map-sdk-amap-850`；
 * 2. `map-impl-amap` 只 `compileOnly` 它，拿编译期类型，不进 POM、不进包。
 *
 * 真实接入官方 aar 时：本模块删除，`map-impl-amap` 改
 * `compileOnly("com.amap.api:3dmap:…")`，壳工程注入官方坐标。
 */
android {
    namespace = "com.sky.automap.map.sdk.amap.v910"

    publishing {
        singleVariant("release")
    }
}
