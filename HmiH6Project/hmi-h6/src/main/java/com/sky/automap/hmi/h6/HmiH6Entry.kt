package com.sky.automap.hmi.h6

import com.sky.automap.hmi.api.HmiEntry

/**
 * H6 车型 HMI 入口（HMI 独立工程，UI 视觉 / 车型专属）。
 *
 * 与 H9 的差异：导航图、视觉主色、首页文案均由本模块独立维护，
 * 壳工程打包 H6 渠道时只会把本模块打进去。
 *
 * **对外契约**：壳工程的 `PlatformAssembler.hmiEntry()` 直接 import 本类
 * （`object` 单例、包名 + 类名硬引用），改名 / 换包会让壳工程编译失败，
 * 改这里必须同步 `CarMapProject/app/src/h6/java/com/sky/automap/assemble/PlatformAssembler.kt`。
 */
object HmiH6Entry : HmiEntry {

    override val modelName: String = "H6"

    override val displayName: String = "H6 车型 HMI"

    override val themeColor: Int = 0xFF2F6BFF.toInt()

    override fun navGraphId(): Int = R.navigation.hmi_h6_graph
}
