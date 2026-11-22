# CarMapProject（壳工程）

只负责**组装与打包**，不含任何业务代码（独立 Gradle 工程，可单独打开）。

## 产物

一个渠道一个 APK，文件名带渠道 ID（键名见 `app-builds.json`）：

- `CarMap_<渠道ID>_<buildType>_v<versionName>_<versionCode>_<时间戳>.apk`
- applicationId 按车型：`com.sky.automap.<carModel 小写>`（如 `com.sky.automap.h6`）
- 输出目录：`app/build/outputs/renamed-apk/<渠道ID>/<buildType>/`

## 组装规则

全部由 `app-builds.json` 决定，`app/build.gradle.kts` 里没有车型/厂商的硬编码映射：

| 配置 | 决定什么 |
|---|---|
| `carModels.<车型>.mapVendor` | 该车型唯一可用的地图厂商（H6→AMAP，H9→BAIDU）；渠道写了别的厂商，配置阶段直接报错 |
| `carModels.<车型>.mapVersions` | 该车型允许的 SDK 版本；渠道写了别的版本，配置阶段直接报错 |
| `builds.<渠道ID>` | 一个键 = 一个 productFlavor，决定 HMI AAR（`hmi-<车型>`）、厂商实现（`map-impl-<厂商>`）、SDK AAR（`map-sdk-<厂商>-<版本>`）与 BuildConfig |

车型装配体 `PlatformAssembler` 位于车型源集 `app/src/<车型>/java/com/sky/automap/assemble/`，同包同名，编译期二选一（由 `carModel` 挂到各渠道的 flavor 源集上）。

## 构建

```bash
./gradlew assembleDebug                                    # 全部渠道的 Debug 包
./gradlew assembleE245_h6_8295_front_1920x720_amap850Debug  # 只打一个渠道
./gradlew :app:assembleDefault                              # 只打「默认渠道」（app-builds.json 的 defaultChannel）
./gradlew :app:installDefault                               # 默认渠道装到设备
```

> IDE 的 Build Variants 默认项由 Android Studio 按变体名字母序自己挑（AGP 没有「默认变体」API，脚本改不了），
> 想固定下来就在下拉框里手动选一次；命令行 / CI 用 `assembleDefault` / `installDefault`。

## 发布流水线（`projects.json`）

`build-all.py` 的发布顺序来自 `projects.json` 的 `publishOrder`（基建层 → 地图适配层 → 业务组件层 → HMI 契约），
HMI 车型工程由 `app-builds.json` 的 `carModels` 自动追加（`h6` → `HmiH6Project`，artifact `hmi-h6`），
所以**新增车型不用改本文件**。`versions.py bump` 的「这个 artifact 属于哪个工程」
也都从这两份 JSON 推，脚本里没有工程名硬编码。

## 前置

需先把上游 AAR 发布到本工程内的 `maven-repo/`：在本目录执行 `./build-all.py`（只发布 AAR；要顺带打 APK 加 `--assemble`），或按 `README.md` 的顺序逐个 `./gradlew publish`。

`maven-repo/` 与 `versions.properties` 都在本工程目录里，所以**整个工程目录拷走后可独立编译**，不再依赖上一级的 6 个库工程。

## AAR 版本表（`versions.properties`）

全仓库唯一版本来源，12 个 artifact 各一行：

```properties
infrastructure=1.0.0
map-sdk-amap-850=1.0.0     ← 改这一行就只升这一个，其余不受影响
hmi-h6=1.0.0
...
```

- 谁在读：6 个库工程的 `skyVersionOf(...)` 与本工程 `app/build.gradle.kts` 都**直接读本文件**（纯文本行解析，不引脚本插件）；`gradle.properties` 里不再有版本段，也就不存在副本不一致。
- 怎么改：在本目录执行 `./versions.py bump map-sdk-amap-850 1.0.1`（等价于手改本文件），再 `publish` 对应工程。
- 与 APK 版本无关：`versionName/versionCode` 只在 `build.gradle.kts` 的 `skyBuild.*` 声明。

## AAR 产物（`maven-repo/`）

全仓库只有这一处 AAR 产物，既是编译期的依赖源，也是需要递 aar 时取文件的地方：

- 结构：`maven-repo/com/sky/automap/<模块>/<版本>/<模块>-<版本>.aar`（同目录还有 POM、module 元数据、sha1）。
- 12 个模块：`infrastructure`、`map-api`（契约 + 门面）、`map-impl-amap`、`map-impl-baidu`、`map-sdk-amap-850/-910`、`map-sdk-baidu-v21/-v22`、`business`、`hmi-api`、`hmi-h6`、`hmi-h9`。
- 不入库，由 `./build-all.py` 重建；要单独刷新某个工程：在该工程下 `./gradlew publish`。
- 不参与打包的判定：APK 里打什么由 `app-builds.json` 的 `carModels` / `builds` 决定（`app/build.gradle.kts` 逐渠道生成依赖），与目录结构无关。

## 本地配置

`local.properties` 需配置 `sdk.dir` 与签名（`app.debug.*` / `app.release.*`，指向 `app` 模块目录下的 `../SkyBuildLogic.jks`），参考上一级 `README.md` 第八节。
