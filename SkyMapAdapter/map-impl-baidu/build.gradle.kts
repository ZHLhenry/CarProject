plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 百度地图实现（厂商公共层 + 全部版本绑定）。
 *
 * 包结构：
 * ```
 * com.sky.automap.map.impl.baidu              ← 厂商公共层（不 import com.baidu.mapapi.*）
 * com.sky.automap.map.impl.baidu.spi          ← SPI
 * com.sky.automap.map.impl.baidu.binding      ← 版本枚举 + 版本 → binding 的工厂
 * com.sky.automap.map.impl.baidu.binding.v21  ← v21 适配（继承链基类）
 * com.sky.automap.map.impl.baidu.binding.v22  ← v22，只写差异
 * ```
 */
android {
    namespace = "com.sky.automap.map.impl.baidu"

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // 依赖契约 + 门面层（同工程 project 依赖，随 AAR 以 com.sky.automap:map-api 坐标写入 POM）。
    // 本模块只允许用其中的 `map.api` 包（接口 / 数据模型），不引用 `map.factory` 包
    // （MapManager / 注册表）—— 这条是约定，不是编译期隔离，评审时留意。
    api(project(":map-api"))

    // 只用于编译：`map-sdk-baidu-v22`（v22 的 API 面 + 两版并集基线）。
    // 两段适配代码共用一条 classpath，且两个 SDK 模块里同名类会互相遮蔽，
    // 必须有一份并集。不进 POM、不进包——运行时由壳工程按车型注入唯一一份
    // （h9 → map-sdk-baidu-v22，真实接入 → 官方 aar）。
    compileOnly(project(":map-sdk-baidu-v22"))

    // 真实接入时把上面一行换成本工程所需的官方坐标（同样只 compileOnly）：
    // compileOnly("com.baidu.lbsapi:base:latest.integration")
    // compileOnly("com.baidu.lbsapi:search:latest.integration")
}
