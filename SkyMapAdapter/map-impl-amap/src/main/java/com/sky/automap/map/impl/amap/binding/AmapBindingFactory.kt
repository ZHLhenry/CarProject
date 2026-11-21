package com.sky.automap.map.impl.amap.binding

import com.amap.api.shim.AmapSdk
import com.sky.automap.map.impl.amap.binding.v850.Amap850Binding
import com.sky.automap.map.impl.amap.binding.v910.Amap910Binding
import com.sky.automap.map.impl.amap.spi.AmapBinding

/**
 * 高德可选的 SDK 版本。
 *
 * 用枚举而不是字符串，是为了让「版本写错」在**编译期**就失败：
 * 壳工程写 `AmapVersion.V850`，枚举里没有 V850 就编译不过。
 * 这替代了以前「壳工程写模块坐标、写错要运行时才炸」的弱点。
 */
enum class AmapVersion(val code: String) {
    V850("850"),
    V910("910"),
}

/**
 * 版本 → 绑定实现（继承链的末端就是这里 new 出来的那个类）。
 *
 * 两个版本现在住在同一个模块的两个包里（`binding.v850 / v910`），
 * 而不是两个 Gradle 模块。选版本只发生在这里，厂商层 [com.sky.automap.map.impl.amap.AmapMapProvider]
 * 依旧不 import 任何 `com.amap.api.*`，也不比对版本号。
 *
 * 与 [com.sky.automap.map.impl.amap.spi.AmapBinding] 的分工（两者名字只差一个 s，别搞混）：
 * - `spi.AmapBinding`（单数，接口）= **一个**版本必须实现什么；
 * - 本文件（工厂）= **按版本挑一个**实例出来。
 * 前者是"长什么样"，后者是"要哪一个"。
 */
object AmapBindingFactory {

    /**
     * 取某版本的绑定实现。
     *
     * 同时把影子层切到该版本——真实接入官方 aar 时没有 `AmapSdk` 这个类，
     * **那一行（`AmapSdk.mode = ...`）删掉即可**，其余代码不用动。
     */
    fun of(version: AmapVersion): AmapBinding {
        AmapSdk.mode = version.code
        return when (version) {
            AmapVersion.V850 -> Amap850Binding()
            AmapVersion.V910 -> Amap910Binding()
        }
    }
}
