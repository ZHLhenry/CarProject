// ============================================================================
// CarMapProject —— 壳工程（独立工程，负责组装与打包）
//
// 只包含一个 application 模块（:app），不承载任何业务代码：
//   1. 以 AAR 方式依赖基建层、地图适配层、业务组件层、HMI 车型工程
//   2. 一个「渠道」= 一个 productFlavor（app-builds.json），渠道决定参数与 SDK 版本
//   3. 车型不是 flavor：按 carModel 把 src/<车型> 挂到渠道源集，编译期二选一
//   4. 按车型覆盖 applicationId（H6 / H9 两个包名可同时安装）
// 构建：./gradlew :app:assembleDefault（默认渠道）/ ./gradlew assembleDebug（全部渠道）
// ============================================================================

// AAR 版本表：唯一来源是 versions.properties，由 ./versions.py sync 同步到各工程
// gradle.properties 的 automap.version.* 键；:app 模块直接读这些属性（见 app/build.gradle.kts），
// 这里不再中转任何中间变量 —— 根脚本里的顶层语句越少，IDE 的脚本分析越不会出错。
// SkyBuild 全局共享配置（applicationId 为壳工程基准包名，具体车型由 :app 的 Flavor 覆盖）
extra["skyBuild.appName"] = "CarMap"
extra["skyBuild.applicationId"] = "com.sky.automap"

// ★ 应用版本号的唯一来源（全仓库只有这里声明 versionCode / versionName）
//   SkyBuildLogic 把它们写给 android.defaultConfig（ApplicationDefaultConfig），
//   最终进 APK 的 AndroidManifest：versionCode=100、versionName=1.0.0。
//   其余 6 个工程都是 library，AGP 没有「库的版本号」这种东西，写了不生效（已实测）。
//
//   注意仓库里有两套互不相干的版本号：
//     1. 应用版本（本节）→ 进 APK，用户/车机看到的版本，改这里 + 重新打包即可；
//     2. AAR 版本（CarMapProject 下的 versions.properties，按 artifact 独立）→ Maven 坐标里的 1.0.0，
//        只决定壳工程解析到哪一份 AAR，改它必须重新发布 AAR 才生效。
//   `./versions.py bump <artifact> x.y.z` 或 `./versions.py bump --all x.y.z` 只动第 2 套；第 1 套需手工改本节。
extra["skyBuild.versionCode"] = 100
extra["skyBuild.versionName"] = "1.0.0"
extra["skyBuild.compileSdk"] = 37
extra["skyBuild.minSdk"] = 24
extra["skyBuild.targetSdk"] = 35
extra["skyBuild.enableViewBinding"] = true
extra["skyBuild.enableDataBinding"] = false
extra["skyBuild.enableBuildConfig"] = true
extra["skyBuild.enableCompose"] = false

// 上游 6 个工程的 AAR 发布到本工程内 maven-repo/（见 settings.gradle.kts 的 skyLocalRepo），
// 编译期按 com.sky.automap:* 坐标解析；工程目录自带全部依赖产物，整体拷走即可编译。
// 需要把 aar 递给外部时，直接从 maven-repo/com/sky/automap/<模块>/<版本>/ 里取。
