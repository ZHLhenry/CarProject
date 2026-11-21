package com.sky.automap.hmi.h9

import com.sky.automap.hmi.api.HmiEntry

/**
 * H9 车型 HMI 入口（HMI 独立工程，UI 视觉 / 车型专属）。
 *
 * 与 H6 的差异：导航图、视觉主色、首页文案均由本模块独立维护，
 * 壳工程打包 H9 渠道时只会把本模块打进去。
 *
 * **对外契约**：壳工程的 `PlatformAssembler.hmiEntry()` 直接 import 本类
 * （`object` 单例、包名 + 类名硬引用），改名 / 换包会让壳工程编译失败，
 * 改这里必须同步 `CarMapProject/app/src/h9/java/com/sky/automap/assemble/PlatformAssembler.kt`。
 */
object HmiH9Entry : HmiEntry {

    override val modelName: String = "H9"

    override val displayName: String = "H9 车型 HMI"

    override val themeColor: Int = 0xFFB8860B.toInt()

    override fun navGraphId(): Int = R.navigation.hmi_h9_graph
}
