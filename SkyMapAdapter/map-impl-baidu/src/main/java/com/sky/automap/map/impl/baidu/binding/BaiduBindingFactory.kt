package com.sky.automap.map.impl.baidu.binding

import com.baidu.mapapi.shim.BaiduSdk
import com.sky.automap.map.impl.baidu.binding.v21.BaiduV21Binding
import com.sky.automap.map.impl.baidu.binding.v22.BaiduV22Binding
import com.sky.automap.map.impl.baidu.spi.BaiduBinding

/**
 * 百度地图可选的 SDK 版本。
 *
 * 与高德侧同构：枚举保证「版本写错」在编译期失败。
 */
enum class BaiduVersion(val code: String) {
    V21("v21"),
    V22("v22"),
}

/**
 * 版本 → 绑定实现。
 *
 * 两个版本住在同一个模块的两个包里（`binding.v21 / v22`），选版本只发生在这里。
 */
object BaiduBindingFactory {

    /** 同时把影子层切到该版本；真实接入官方 aar 时删掉 `BaiduSdk.mode = ...` 这一行即可 */
    fun of(version: BaiduVersion): BaiduBinding {
        BaiduSdk.mode = version.code
        return when (version) {
            BaiduVersion.V21 -> BaiduV21Binding()
            BaiduVersion.V22 -> BaiduV22Binding()
        }
    }
}
