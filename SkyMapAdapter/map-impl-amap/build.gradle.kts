plugins {
    alias(libs.plugins.sky.android.library.common)
}

/**
 * 高德地图实现（厂商公共层 + 全部版本绑定）。
 *
 * 包结构就是分层：
 * ```
 * com.sky.automap.map.impl.amap             ← 厂商公共层（不 import com.amap.api.*）
 * com.sky.automap.map.impl.amap.spi         ← 厂商层与版本层之间的 SPI
 * com.sky.automap.map.impl.amap.binding     ← 版本枚举 + 版本 → binding 的工厂
 * com.sky.automap.map.impl.amap.binding.v850 ← 850，只写差异
 * com.sky.automap.map.impl.amap.binding.v910 ← 910，只写差异
 * ```
 *
 * 为什么版本用「包」而不是「模块」：
 * 版本适配代码量很小（850 约 150 行、910 约 90 行），
 * 拆成三个 Gradle 模块要多付三份 build 脚本、三份发布配置、三份 wrapper，
 * 换来的只有「编译期各自看到自己版本的 API」——这一条已由
 * 静态校验任务 `verifyMapSdkChain` + 运行期 `AmapSdk.gate` 补上。
 */
android {
    namespace = "com.sky.automap.map.impl.amap"

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // 依赖契约 + 门面层（同工程 project 依赖，随 AAR 以 com.sky.automap:map-api 坐标写入 POM）。
    // 本模块只允许用其中的 `map.api` 包（接口 / 数据模型），不引用 `map.factory` 包
    // （MapManager / 注册表）—— 这条是约定，不是编译期隔离，评审时留意。
    api(project(":map-api"))

    // 只用于编译：`map-sdk-amap-910`（910 的 API 面 + 三版并集基线）。
    // 为什么是 910 而不是「三个版本都挂上」：三段适配代码共用一条 classpath，
    // 而三个模块里同名类会互相遮蔽（谁先被扫到就用谁），必须有一份并集。
    // 它不进 POM、不进包——运行时真正的那一份由壳工程按车型唯一注入
    // （h6 → map-sdk-amap-850，真实接入 → 官方 aar）。
    compileOnly(project(":map-sdk-amap-910"))

    // 真实接入时把上面一行换成本工程所需的官方坐标（同样只 compileOnly）：
    // compileOnly("com.amap.api:3dmap:9.1.0")
    // compileOnly("com.amap.api:search:9.1.0")
}
