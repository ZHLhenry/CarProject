// 根脚本只声明插件版本（apply false），具体模块各自应用。
// 与仓库内其它工程不同：这里显式用标准 AGP 插件，不用 SkyBuildLogic ——
// 外部方没有我们的 convention 插件，本工程就是给他们照抄的最小样本。
plugins {
    alias(libs.plugins.android.application) apply false
}
