# CarMap 多工程结构（按架构图拆分）

按架构图把车机地图拆成 **7 个互相独立的 Gradle 工程**：每个工程都能单独用 Android Studio 打开、单独编译、单独发版，跨工程之间只通过 **AAR + Maven 坐标** 耦合。

```
lib/carProject/
├── SkyInfrastructure/      基建层        → infrastructure.aar
├── SkyMapAdapter/          地图适配层    → 7 个模块：接口+门面 / 厂商实现（含版本链）/ 一版一份 SDK
├── SkyBusinessComponent/   业务组件层    → business（一个 AAR，内含 poi / navigation / route 三个包）
├── SkyHmiApi/              契约层        → hmi-api（车型插件契约）+ car-api（对外 AIDL 契约）
├── HmiH6Project/           H6 车型 HMI   → hmi-h6
├── HmiH9Project/           H9 车型 HMI   → hmi-h9
├── CarMapProject/          壳工程        → 按渠道出 APK：CarMap_h6_E245_FRONT_1920x720_amap850 / CarMap_h9_..._baiduMapV22
│   ├── maven-repo/         本地 Maven 仓库（6 个上游工程 publish 的产物，编译期依赖源）
│   ├── versions.properties ★ AAR 版本表：13 个 artifact 各一行，全仓库唯一来源
│   ├── app-builds.json    ★ 渠道表：APP_BUILD_ID → 车型/芯片/产品/屏型/分辨率/地图厂商与版本
│   ├── build-all.py        一键按序发布 AAR（--assemble 才顺带打 APK）
│   ├── versions.py         版本表维护脚本：list 查看 / bump 升版（只改 versions.properties，无同步步骤）
│   ├── projects.json      ★ 发布流水线清单：固定架构栈顺序 + artifact → 工程
│   ├── pipeline.py         上面几个 py 脚本共用的配置读取（不硬编码工程名）
│   └── （无脚本插件：版本号由各工程 build.gradle.kts 直接读 versions.properties）
└── CarClientDemo/          AIDL 调用演示（**不属于架构栈**：模拟外部应用，只依赖 car-api 的 AAR）
```

> 脚本全部收在 `CarMapProject/` 里。`CarClientDemo` 是第八个工程，但它扮演「第三方应用」，
> 不参与 `build-all.py` 发布流水线，也不依赖任何库工程的源码。

## 一、工程职责与依赖关系

| 工程 | 角色（对应架构图） | 产物（groupId `com.sky.automap`） | 依赖的下游 AAR |
|---|---|---|---|
| `SkyInfrastructure` | Infrastructure 基建层：SkyMVVM / SkyWidget / SkyMultiStateLayout 的统一出口、多状态布局约定 | `infrastructure` | 无（红线：不依赖任何地图、业务、HMI） |
| `SkyMapAdapter` | SkyMapAdapter 地图适配：契约 + 门面（同一个 AAR 两个包）+ 厂商实现层（含 SDK 版本绑定**链**，版本用包区分）+ 一版一份影子 SDK | `map-api`、`map-impl-amap`、`map-impl-baidu`、`map-sdk-amap-850/-910`、`map-sdk-baidu-v21/-v22`（共 7 个） | 无（本工程内部依赖） |
| `SkyBusinessComponent` | SkyBusinessComponent 业务组件：POI / 导航 / 路线（**一个 AAR，内部按业务分包**） | `business` | `infrastructure`、`map-api` |
| `SkyHmiApi` | 契约层，两个模块两种受众：`hmi-api` = 车型 HMI 与壳工程之间的约定（同进程、编译期二选一）；`car-api` = **对外 AIDL 契约**（跨进程、给第三方应用、要求二进制兼容） | `hmi-api`、`car-api` | 无 |
| `HmiH6Project` | H6 车型 HMI 独立工程（UI 视觉 + 车型交互） | `hmi-h6` | `hmi-api`、`infrastructure`、`business` |
| `HmiH9Project` | H9 车型 HMI 独立工程 | `hmi-h9` | 同上（与 H6 工程零依赖） |
| `CarMapProject` | 壳工程：只做组装与打包，不含业务代码 | APK（`com.sky.automap.h6` / `.h9`） | 以上全部；厂商与 SDK 版本按 `app-builds.json` 的渠道表逐渠道注入 |

依赖方向与架构图一致，红线仍然成立：`infrastructure` 不依赖任何 `map-*`；业务组件只依赖 `map-api`（契约/数据模型 + 门面，一个坐标），不依赖任何厂商实现或版本适配包；HMI 工程之间互不依赖。

### map-api 内部两个包的分工（严格分法）

`map-api` 是一个 AAR，靠**包边界**而不是模块边界分层：

| | `map.api` 包 | `map.factory` 包 |
|---|---|---|
| 内容 | `IMapProvider`、`IMapView`、`MapCapabilities`、`LatLng`/`MapMarker`/`MapPoi`、`MapSdkType` | `MapManager` 门面 + `MapProviderRegistry`（`internal`，不导出） |
| 状态 | **零可变状态**，纯只读契约 | 持有「当前生效实现」，负责注入/取用/兜底 |
| 变更成本 | 接口一动全线重编，必须向后兼容 | 内部随便加降级、多实例池、埋点，契约不受影响 |
| 谁用 | 业务组件 / 两个厂商实现层 / 壳工程 | 业务组件、壳工程 |
| 类比 | 插座国标（定义两孔 220V） | 配电箱（决定这间屋子接哪路电） |

> 门面原本单独是 `map-factory` 模块，只有两个文件却要多一份 build 脚本、多一个坐标、多一行版本表；
> 而业务组件与壳工程本来就要依赖 `map-api`，因此合并为同一个 AAR 的两个包。
> 代价：「契约零可变状态」从模块边界退化为包边界，靠评审与下面这条红线保证。

业务组件里**不允许出现** `MapProviderRegistry`、`AmapMapProvider`、`BaiduV22Binding`，统一走 `MapManager.createMapView(...)` / `searchPoi(...)` / `planRoute(...)` / `capabilities()`。

## 二、跨工程怎么"连"起来

不引入私服，用一个**本地 Maven 仓库目录**作为唯一中介。这个仓库就放在壳工程里：`CarMapProject/maven-repo/`——这样 `CarMapProject` 目录自带全部上游产物，整体拷走就能编译。

- 每个库工程都配了 `maven-publish`：`./gradlew publish` 输出到 `../CarMapProject/maven-repo/com/sky/automap/<模块名>/<版本>/`（AAR + POM + Gradle module 元数据，POM 里带完整的传递依赖）。
- 每个工程的 `settings.gradle.kts` 都把 `../CarMapProject/maven-repo`（壳工程自己是 `${rootDir}/maven-repo`）放在仓库列表**第一位**，下游工程直接命中本地 AAR，不走远程。
- 坐标统一：`group = com.sky.automap`、`artifactId = 模块名`、`version = CarMapProject/versions.properties 里该 artifact 那一行`。
- 版本号按 artifact 分开记（唯一来源 `CarMapProject/versions.properties`，全仓库只此一份，各工程 build.gradle.kts 直接读它），因此**允许单模块升版**：只把某个 AAR 升到 1.0.1，其余仍是 1.0.0。
- 同工程内部的模块（如 `map-impl-amap` → `map-api`）仍用 `project(":map-api")` 依赖，发布时自动映射成同样的 Maven 坐标，本地开发不必先 publish。
- 全仓库只有 `CarMapProject/maven-repo/` 这一份 AAR 产物（带 POM 的仓库形态），没有额外的平铺导出目录——同一批文件不存两份，改版本后只需重建这一处。

```kotlin
// 壳工程 app/build.gradle.kts —— 依赖按渠道表逐 flavor 注入，不手写 artifact
// 车型决定 HMI + 地图厂商，渠道（键名）决定 SDK 版本：
//   hmi-<车型> / map-impl-<厂商> / map-sdk-<厂商>-<版本>
appBuilds.values.forEach { build ->
    add("${build.id}Implementation", "com.sky.automap:hmi-${build.carModel.lowercase()}:...")
    add("${build.id}Implementation", "com.sky.automap:map-impl-${build.mapVendor.lowercase()}:...")
    add("${build.id}Implementation", "com.sky.automap:map-sdk-${build.mapVendor.lowercase()}-${build.mapVersion}:...")
}
// 版本号都来自 versions.properties 的 skyVersionOf(...)；
// PlatformAssembler 不再写死版本，而是按 BuildConfig.APP_MAP_VERSION 选 binding：
//   AmapMapProvider(AmapBindingFactory.of(AmapVersion.of(BuildConfig.APP_MAP_VERSION)))
```

## 三、构建（必须按依赖顺序）

单个工程：

```bash
cd SkyInfrastructure    && ./gradlew publish      # 基建层
cd ../SkyMapAdapter     && ./gradlew verifyMapSdkChain && ./gradlew publish   # 地图适配层（静态检查 + 发布 7 个模块）
cd ../SkyBusinessComponent && ./gradlew publish   # 业务组件层
cd ../SkyHmiApi         && ./gradlew publish      # HMI 契约
cd ../HmiH6Project      && ./gradlew publish      # H6 HMI（改 H9 同理）
cd ../CarMapProject     && ./gradlew assembleDebug # 壳工程出包
```

一键脚本（推荐）：

```bash
./CarMapProject/build-all.py                 # 只按序发布全部 AAR（不打 APK）
./CarMapProject/build-all.py --assemble      # 发布 + 打「默认渠道」Debug APK
./CarMapProject/build-all.py --assemble --release   # 发布 + 默认渠道 Release
./CarMapProject/build-all.py --all-channels  # 发布 + 打全部渠道
```

### AAR 产物在哪

`publish` 的产物在 `CarMapProject/maven-repo/com/sky/automap/<模块>/<版本>/` 下（`<模块>-<版本>.aar` + POM + module 元数据 + 校验和）。
要给外部递 aar，直接从这些目录里取那 13 个 `.aar` 即可——没有额外的「平铺导出」目录，避免同一批产物存两份。

产物（文件名里带渠道 ID，拿到包不用解压就知道装的是哪个渠道）：

```
CarMapProject/app/build/outputs/renamed-apk/e245_h6_8295_front_1920x720_amap850/debug/CarMap_e245_h6_8295_front_1920x720_amap850_debug_v1.0.0_100_20261121145645.apk
```

命名规则：`CarMap_<渠道ID>_<buildType>_v<versionName>_<versionCode>_<yyyyMMddHHmmss>.apk`，
渠道 ID 就是 `app-builds.json` 的键，所以改渠道 / 改 SDK 版本时文件名自动跟着变，不需要手工同步。

> `SkyMapAdapter/buildSrc` 是「继承链校验任务」的实现（读 class 文件，依赖 `org.ow2.asm:asm`），
> 首次构建会联网拉一次依赖；报告输出在 `SkyMapAdapter/build/reports/sdk-chain/{amap,baidu}.md`。
> 它的必要性：版本适配改成「包」之后，编译期不再按版本隔离 API，
> 「被新版移除的 API 是否还有人调」只能靠这条静态检查兜住（规则见 `SkyMapAdapter/README.md` 第五节）。

## 四、多包名（多车型）与渠道（APP_BUILD_ID）

包名由 Flavor 决定，**其余差异全部由渠道决定**（芯片 / 产品 / 屏型 / 分辨率 / 地图版本）：

| 车型 | applicationId | 应用名 | 注入的 HMI | 允许的地图厂商 |
|---|---|---|---|---|
| `h6` | `com.sky.automap.h6` | CarMap H6 | `hmi-h6` | 高德（`map-impl-amap`），850 / 910 |
| `h9` | `com.sky.automap.h9` | CarMap H9 | `hmi-h9` | 百度（`map-impl-baidu`），v21 / v22 |

- 车型不是 flavor：`carModel` 决定挂哪套 HMI 与厂商，把 `app/src/<车型>` 注入该渠道的 flavor 源集。
- 包名在 `app/build.gradle.kts` 的 `androidComponents.onVariants` 里按车型覆盖（`sky.android.application` 会用 `skyBuild.applicationId` 统一赋值，必须在 Variant 阶段改）。
- 车型装配体 `PlatformAssembler` 放在车型源集 `app/src/h6`、`app/src/h9`，同包同名，**编译期二选一**，运行时没有任何 if/else。
- 两个车型包名不同，可同机并装；同一车型的不同渠道 applicationId 相同，装同一台设备会互相覆盖（预期行为）。

### 渠道表：`app-builds.json`

唯一来源，三段：`carModels`（车型 → 厂商 + 允许版本）、`builds`（渠道）、`defaultChannel`（默认渠道）。
渠道键名约定：`{产品}_{车型}_{芯片}_{屏型}_{宽}x{高}_{地图}{版本}`，**全小写**，例 `e245_h6_8295_front_1920x720_amap850`。
键名就是 flavor 名（任务名 = `assemble` + 首字母大写键名 + `Debug`）；构建逻辑只读字段、不解析键名。

```json
"carModels": {
  "h6": { "mapVendor": "AMAP", "mapVersions": ["850", "910"] },
  "h9": { "mapVendor": "BAIDU", "mapVersions": ["v21", "v22"] }
},
"builds": {
  "e245_h6_8295_ceiling_1280x720_amap910": {
    "carModel": "H6",         // 必须是 carModels 里声明过的车型
    "chip": "8295",
    "product": "E245",
    "screenType": "CEILING",  // 与 ChannelConfig.ScreenType 同名
    "screenWidth": 1280,
    "screenHeight": 720,
    "mapVendor": "AMAP",      // 必须等于 carModels.h6.mapVendor
    "mapVersion": "910"       // 必须在 carModels.h6.mapVersions 里
  }
},
"defaultChannel": "e245_h6_8295_front_1920x720_amap850"
```

打包：

```bash
./gradlew :app:assembleDefault                                       # 默认渠道的 Debug 包
./gradlew :app:assembleE245_h6_8295_ceiling_1280x720_amap910Debug    # 指定渠道
./gradlew assembleDebug                                              # 全部渠道
```

产物名自带渠道 ID（就是 JSON 的键）：

```
CarMap_e245_h6_8295_front_1920x720_amap850_debug_v1.0.0_100_<ts>.apk
CarMap_e245_h6_8295_ceiling_1280x720_amap910_debug_v1.0.0_100_<ts>.apk
```

### 参数怎么流到代码里

```
app-builds.json                      ← 唯一来源，人只改这里
  → app/build.gradle.kts             ← 配置阶段直接解析（校验车型/厂商/版本/屏型），
                                       每个键生成一个 productFlavor
  → BuildConfig.APP_*                ← 构建期按变体注入
  → ChannelConfig                    ← 运行期唯一入口
```

```kotlin
when (ChannelConfig.screenType) {
    ScreenType.FRONT   -> loadFrontLayout()
    ScreenType.REAR    -> loadRearLayout()
    ScreenType.CEILING -> loadCeilingLayout()
    ScreenType.ARMREST -> loadArmrestLayout()
}
if (ChannelConfig.screenSpec.longSide >= 2560) loadHighResAssets()
```

启动时会打一行渠道日志，装错包第一时间能看出来：

```
CarMap: APP_BUILD_ID=E245_H6_8295_FRONT_1920x720_AMAP850 car=H6 chip=8295 product=E245 screen=FRONT(1920x720) map=AMAP:850
```

### 渠道能改什么、不能改什么

- **能改**：地图 SDK 版本（`850/910`、`v21/v22`）、屏型、分辨率、芯片、产品名。
  版本会真正切换进包的 SDK（`map-sdk-amap-850` ↔ `map-sdk-amap-910`），
  同时 `PlatformAssembler` 按 `BuildConfig.APP_MAP_VERSION` 选对应 binding，
  两边对不上时影子 SDK 的版本标定会在启动第一秒抛异常。
- **不能改**：地图厂商。一个 APK 只打一份 SDK，厂商由车型工程决定
  （`hmi-h6` 源集只 import 高德实现，`hmi-h9` 只 import 百度）。
  渠道里写错厂商，配置阶段会带着渠道 ID 直接报错，不会静默出包。
- 改完 `app-builds.json` 直接构建即可，**不需要同步任何东西**（`app/build.gradle.kts` 配置阶段直接解析它）。
  这两个文件都放在 `CarMapProject/` 内：渠道只影响壳工程打包，6 个库工程完全不感知。

实测裁剪结果（解包 dex 校验）：
- H6 包：dex 里只有 850 的 API 类（`com.amap.api.maps.*` 无 `AMapOptions`、无 `updatePrivacyShow`），百度类 0 处；`Amap850Binding`（基类）/ `Amap910Binding` 两段适配在同一 AAR 里。
- H9 包：dex 里只有百度的类（`BaiduMapOptions` 在位），高德类 0 处。
- 两个包都正确合并了 `poi/navi/route` 三张业务子图与 HMI 主图。
- 跨厂商隔离保持：H6 里没有任何 `com.baidu.*`，H9 里没有任何 `com.amap.*`；两包都正确合并了 `poi/navi/route` 三张业务子图与 HMI 主图。

> 代价说明：版本用「包」区分后，**同一厂商所有版本的适配代码都会进包**（850 + 910 约 2 KB），
> 换来的是模块数从 14 降到 6、版本选择从「Maven 坐标」变成「编译期枚举」。
> 若某厂商适配代码体量变大（比如超过 200 KB），再拆回「一版本一模块」也只需把包升级为模块，
> 上层装配代码只改 import。

### 版本绑定为什么是「继承链」而不是「每版一份实现」

这是整套地图适配里最容易写错的地方：**910 不要复制 850 的功能，只写差异。**

```
binding.v850.Amap850Binding   公共适配代码的唯一一份（初始化/建图/检索/路线/标注/生命周期）+ 850 的路况
     ↑
binding.v850.Amap850Binding   只写增量：能力位补路况、建图后打开路况（约 30 行）
     ↑
binding.v910.Amap910Binding   只写改动：隐私合规、构造参数、同步检索、多路线（约 80 行）
```

「未变动的功能还能调到 850 的代码」在两个层面同时成立：
- 源码层：`open` + `override`，子类没覆写就继承父类实现；
- 字节码层：厂商实现的 SDK 依赖是 `compileOnly`，由壳工程唯一注入一份 SDK 后，
  850 写的代码运行时执行的是「进包那份 SDK」中的同名方法（SDK 保持二进制向后兼容）。

四条硬规则（破坏任一条链就断）：

1. **可能被新版本改掉/移除的 API 调用，只能出现在可覆写的方法里**，不许写进构造函数、`init` 块或 `final` 方法——否则子类没有覆写点，运行期 `NoSuchMethodError`。
2. **覆写被移除 API 所在的方法时，不许再调 `super`**：增量改动（父类方法体干净）才 `super.xxx()` 复用，破坏性变更（父类方法体里就是被移除的 API）必须整段替换。这条由 `verifyMapSdkChain` 用字节码 `invokespecial` 检查，见 `SkyMapAdapter/README.md` 第四节。
3. **SDK 依赖不许写 `implementation`**：那会随 AAR 传递，两个版本的 SDK 一起进包 → duplicate class。厂商实现侧 `compileOnly`，壳工程按车型 `implementation` 唯一一份。
4. **厂商层不 import SDK、业务层不比较版本号**：前者保证公共逻辑只写一次，后者保证新增版本时业务组件（`business.aar` 里的 poi / navigation / route）不用重编。业务层只用能力位：
   `if (MapManager.capabilities().supportsOfflineMap) showOfflineEntry()`。

新增版本的完整清单（含「什么时候不要用继承」的判断）见 `SkyMapAdapter/README.md`。

## 五、新增车型（例：H10）

车型是数据，不是 flavor，所以壳工程侧**只改 JSON**：

1. 复制 `HmiH9Project` → `HmiH10Project`，改 `settings.gradle.kts` 的 `rootProject.name`、模块目录名、`android.namespace`、`gradle.properties`。
2. HMI 工程里新增 `HmiH10Entry`（实现 `hmi-api` 的 `HmiEntry`）、首页 Fragment 与导航图，`./gradlew publish` 发布 `hmi-h10`。
3. `CarMapProject/versions.properties` 加一行 `hmi-h10=1.0.0`（`build-all.py` 会自动追加 `HmiH10Project` 到发布顺序，无需改脚本）。
4. `CarMapProject/app-builds.json` 的 `carModels` 加一段：`"h10": { "mapVendor": "AMAP", "mapVersions": ["910"] }`，再在 `builds` 里加该车型的渠道。
5. 新增车型源集 `app/src/h10/java/com/sky/automap/assemble/PlatformAssembler.kt`（与 h6/h9 同包同名，注入 `HmiH10Entry` + 厂商实现）与 `app/src/h10/res/values/strings.xml`（`app_name`）。
6. 若新车型用新地图厂商：在 `SkyMapAdapter` 里加 `map-impl-<厂商>`（内含 `binding.v*` 包）与各版本 `map-sdk-<厂商>-<版本>`；壳工程只在 `carModels` 里写上新厂商即可，`build.gradle.kts` 一行都不用动。

## 六、新增业务组件

业务层只有一个 AAR（`com.sky.automap:business`），新增组件是在它内部**加一个包**，不是加一个模块：

1. 在 `SkyBusinessComponent/business/src/main/java/com/sky/automap/component/` 下新建包（如 `charging`），写 Fragment + ViewModel；
2. 布局放 `business/src/main/res/layout/`，子导航图放 `business/src/main/res/navigation/charging_graph.xml`；
3. 记得 `import com.sky.automap.component.R`（R 只有一份，namespace 是三个包的公共父级）；
4. 地图能力统一走 `MapManager`，不要碰注册表 / 厂商层 / binding；
5. `./gradlew publish` 后，HMI 工程**不需要改依赖**（已经引了 `business`），只要在 HMI 图里 `<include app:graph="@navigation/charging_graph" />` 即可。

只有当某个组件需要**独立发版**（例如单独卖给某车型定制）时，才把它从包里升级为模块：`include(":charging")` + 独立 `build.gradle.kts` + HMI 加一行坐标，调用方只改 import。

## 七、新增地图 SDK 版本（例：高德 920）

1. 新建 `map-sdk-amap-920`（从 `-910` 复制）：补 920 新增 API（标 `@ApiSince(AmapSdk.V920)`），给 920 移除的成员标 `@ApiRemoved(AmapSdk.V920)`——它是新的编译基线；
2. 新建 `map-impl-amap/.../binding/v920/Amap920Binding : Amap910Binding()`（继承**最近的**上一版，不要继承 850）；
3. 只写差异：能力位 `super.capabilities.copy(...)`、被改掉的 API 覆写，没变的一律不写；
4. `AmapVersion` 加 `V920("920")`、`AmapBindingFactory.of()` 加一个 `when` 分支、`AmapSdk.ORDER` 加 `V920`；
5. `./gradlew verifyMapSdkChain` 必须绿，然后 `./gradlew publish`；
6. 壳工程对应 `PlatformAssembler` 换一个枚举常量。

厂商层、业务组件、HMI 工程**一行都不用改**。

## 八、版本管理（两套版本号，互不相干）

| | 应用版本 | AAR 版本 |
|---|---|---|
| 声明位置 | `CarMapProject/build.gradle.kts` 的 `skyBuild.versionCode/versionName` | `CarMapProject/versions.properties`（13 个 artifact 各一行，唯一来源） |
| 当前值 | `100` / `1.0.0` | 13 个 artifact 均为 `1.0.0`（`car-api` 的接口能力版本另算，看 `CarApi.API_VERSION`） |
| 作用 | 写进 APK 的 `AndroidManifest`（`aapt2 dump badging` 可见），车机/用户看到的版本 | Maven 坐标 `com.sky.automap:<artifact>:<表里那一行>`，只决定壳工程解析到哪份 AAR |
| 生效方式 | 重新打包 | `publish` 对应工程；壳工程下次构建即按新版本解析 |
| 一键修改 | 无（手工改两行） | `./CarMapProject/versions.py bump <artifact> <版本>` 单个 / `--all <版本>` 全部 |

**只有壳工程 `CarMapProject` 能改 APK 版本号。** 因为只有 `:app` 是 `com.android.application`，
SkyBuildLogic 也只把这两个值写给 `ApplicationDefaultConfig`。其余库工程是 library，
AGP 的 `LibraryDefaultConfig` 根本没有这两个属性——写了不会进 AAR（AAR 的 `AndroidManifest.xml` 里
连 `versionCode` 字段都没有），也不影响 APK。**已实测**：把 `SkyHmiApi` 改成 `9.9.9 / 777` 后重新发布，
APK 仍是 `versionCode=100 versionName=1.0.0`。因此这些库里不再声明这两项，只留一条注释指回本表。

> 副作用：库工程的构建日志里 SkyBuildLogic 的配置横幅会打印 `versionCode = (not set)`，属正常现象
>（library 无此属性），`(:app)` 那一份会显示真实值。

- **SDK 版本不进 Maven 版本号**：`map-impl-amap` 的 `version` 仍是工程版本（如 `1.0.0`），SDK 版本由 `AmapVersion` 枚举承载，避免两套版本号互相污染。
- APK 文件名里也带了渠道 ID：`CarMap_<渠道ID>_<buildType>_v<versionName>_<versionCode>_<时间戳>.apk`。

### 单模块升版（各 AAR 版本可以不一致）

`CarMapProject/versions.properties` 按 artifact 逐行记版本，所以可以只升一个：

```bash
./CarMapProject/versions.py bump map-sdk-amap-850 1.0.1   # 只改这一行，其余 13 个仍是 1.0.0
cd SkyMapAdapter && ./gradlew :map-sdk-amap-850:publish
cd ../CarMapProject && ./gradlew :app:assembleDefault   # 打默认渠道即可看到新 AAR
```

机制说明：

- 依赖声明不再写死版本，而是 `com.sky.automap:<artifact>:${skyVersionOf("<artifact>")}`，即「按表取」——表里改一行，全链路（发布版本 + 下游解析 + 各渠道依赖注入）自动跟随，不存在「改了号但对不上」。
- 装载链路就一跳：`versions.properties` →（各工程 `build.gradle.kts` 的 `skyVersionOf`）→ 依赖坐标。人只改 `versions.properties` 一个文件，改完重新 publish 受影响的工程即可，没有「同步」这一步。
- 为什么是「脚本里直接读纯文本」而不是抽成 `versions.gradle.kts` + `apply(from = ...)`：独立 `.gradle.kts` 脚本插件要靠 apply 才能拿到 `Project` 接收者，IDE 对其解析不稳定——命令行一切正常，编辑器里却整篇标红，严重时还会连带把宿主脚本里后续语句判为 Unresolved。`versions.properties` 是纯文本、不参与脚本分析，`build.gradle.kts` 里只剩几行 `readLines()` + `startsWith`，IDE 不会纠结算术。
- 也不在 `extra[...]` 里中转一层 `skyVersions`：多一层间接就多一处 IDE 解析不到的符号，直接调用 `skyVersionOf("<artifact>")` 最稳。
- 唯一需要写脚本的地方是 `skyVersionOf` 本身，注意两个 Kotlin DSL 坑：`java.util.Properties()` 会因 `java` 被 `JavaPluginExtension` 遮蔽而编译失败，所以用 `readLines()`；链式调用写 `?.substringAfter("=")?.trim()`，不能写成 `?.substringAfter("=").trim()`。
- 只有一份表，不存在「表与副本不一致」的问题，因此不再需要 CI 一致性校验。
- **多版本可以共存**：`maven-repo` 里 `map-sdk-amap-850/1.0.0` 与 `1.0.1` 是并列目录，Gradle 按依赖声明取，不会误用旧版。
- `map-sdk-*` 这类「只被壳工程注入、没有别的 AAR 依赖它」的叶子模块，单独升版零风险（实测：只升 850 到 1.0.1，h6 的 runtime classpath 里只有 `map-sdk-amap-850:1.0.1`，其余 7 个仍是 `1.0.0`）。
- 升**被依赖**的模块（如 `map-api`）要注意兼容性：下游 AAR 的 POM 里仍写着旧版本，靠 Gradle 的版本仲裁取高者（壳工程显式声明新版本，会赢）；但那些 AAR 是按旧 API 编的，**必须保证二进制兼容**（只加不改不删）。做不到兼容，就得把依赖它的模块一起重编、一起升版——这才是「版本对齐」真正要保证的东西，而版本号本身只是标签。

## 九、本地配置（每台机器一次性）

每个工程的 `local.properties`（已被 `.gitignore` 忽略，需自行创建）：

```properties
# 所有工程
sdk.dir=/Users/xxx/Library/Android/sdk

# 所有工程：私有 Maven 仓库（阿里云云效）凭据，由 settings.gradle.kts 读取。
# 不配置时按匿名访问，公开依赖仍可解析；缺这两行时若构建早期就拉私有插件/库会报 401。
skyRepo.username=<你的云效账号>
skyRepo.password=<你的云效密码>
```

```properties
# 仅 CarMapProject 还需要签名（路径相对 app 模块目录）
app.debug.storeFile=../SkyBuildLogic.jks
app.debug.storePassword=***
app.debug.keyAlias=SkyBuildLogic
app.release.storeFile=../SkyBuildLogic.jks
app.release.storePassword=***
app.release.keyAlias=SkyBuildLogic
```

签名库 `CarMapProject/SkyBuildLogic.jks` **不入库**（`.gitignore` 里的 `*.jks` / `*.keystore`），
需要签名时自备 keystore 放到该位置，或在 `local.properties` 里把 `storeFile` 指向你自己的文件。
凭据同理：仓库地址写在 `settings.gradle.kts` 里，**账号密码只从 `local.properties` 读**，不要改回硬编码。

## 十、两个说明

1. **地图 SDK 二进制未内置**：`map-sdk-amap-850/-910`、`map-sdk-baidu-v21/-v22` 是「影子 SDK」，一个模块对应一份真实 aar 的包名、类名与调用形状（最新版模块额外保留历史成员，作为 `map-impl-*` 的编译基线，并带 `@ApiSince` / `@ApiRemoved` 标注）。真实接入时这 4 个模块整体替换为官方坐标、壳工程注入唯一一份真实 aar，**厂商层与业务层零改动**（只需删掉 `AmapBindingFactory.of()` 里那行 `AmapSdk.mode = ...`）。
2. **`CarMapProject/maven-repo/` 不入库**：它是各工程 `publish` 的产物目录，随时可由 `./CarMapProject/build-all.py` 重新生成；若 CI 上用私服，只需把各工程根 `build.gradle.kts` 里的 `repositories` 地址换成私服并补 `credentials`。

## 十一、对外提供 AIDL（`car-api`）

外部应用要通过 AIDL 调用车机应用的能力时，**契约、实现、声明分三处**，不要合并：

| 层 | 位置 | 内容 |
|---|---|---|
| 契约 | `SkyHmiApi/car-api`（`com.sky.automap:car-api`） | 只有 `.aidl` + Parcelable 数据契约 + 常量，零业务依赖，是递交给外部方的唯一 AAR |
| 实现 | `CarMapProject/service`（`:service` 模块） | `RemoteCarService`（Stub 实现 + 调用方鉴权）、`RemoteCarBridge`（回调登记 / 就绪广播 / 身份兜底）、`CarServiceEnv`（注入载体）；DTO ↔ domain 转换只发生在这里 |
| 声明 | `:app` 的 manifest（由 `:service` 合并） | `<service android:exported="true">` + 自定义 `signature\|privileged` 权限 |

为什么不放进 `hmi-api`：它是「车型 HMI 插件」契约（同进程、编译期二选一），而 AIDL 是跨进程、要对第三方承诺二进制兼容的接口，两者稳定性等级与发布节奏不同；混在一起相当于把 `HmiEntry` 也暴露给外部。

### 现状

已完成并验证：

- `car-api` 契约 AAR：发布为 `com.sky.automap:car-api:1.0.0`，内含 API 2 的全部方法。**Maven 版本与接口能力版本解耦**：接口有无某方法看 `CarApi.API_VERSION`，不看 Maven 版本（坐标只是发布标签）。
  - API 1：`getApiVersion` / `getIdentity` / 回调注册注销
  - API 2（追加在末尾）：`searchPoi` / `planRoute` / `navigateTo` / `stopNavigation` / `getNavState`
  - 数据契约：`CarIdentity` / `CarPoi` / `CarLatLng` / `CarNavState`（全部手写 Parcelable，字段只追加）
- `:service` 服务端：Stub 实现 + `<service>` 声明（合并进 `:app`）；`:app` 在 `CarMapApplication.onCreate` **末尾**注入 `CarServiceEnv`（放在地图实现 `install` 之后，否则外部收到 `onServiceReady` 时业务还没装好）。
  - `searchPoi` / `planRoute` 转调 `MapManager.provider()`，DTO ↔ 领域模型的转换集中在 `CarDtoMapper.kt`；
  - `navigateTo` / `stopNavigation` 目前是**模拟态**（`map-api` 还没有导航能力），状态放在 `NavStateHolder`，变化时通过 `onNavStateChanged` 推送；接真实导航只需替换该类的 start/stop，契约与外部调用方都不用动。
- 端到端验证：`:app:assembleDefault` 与 `:app:assembleDefaultRelease` 均通过；`aapt2 dump xmltree` 可见 `<service android:exported="true" android:permission="com.sky.automap.permission.BIND_CAR_SERVICE">`、`protectionLevel=0x12`（signature|privileged）、action 与 `CarApi.ACTION_SERVICE` 一致；dex 内含 `RemoteCarService` 与 `IRemoteCarService`。
- `CarClientDemo` 调用演示：第 8 个独立工程（标准 AGP，不用 SkyBuildLogic），只依赖 `car-api` 的 AAR；演示 action 查询、`<queries>` 包可见性、签名权限、`onServiceReady` 语义、binder 死亡监听。构建通过，`aapt2` 确认清单含 `queries` 与绑定权限，dex 含契约类。

未做（下一步）：

- **业务能力仍是示例级**：`navigateTo` 是模拟态；`searchPoi` / `planRoute` 是**同步**直调 `MapManager.provider()`，真实厂商 SDK 多为异步，且需要限流与分页（Binder 单次事务 1MB）。对外承诺前要按产品需求定最终能力集，把重活挪到主线程 + 回调。
- **回调接口的演进代价**：给 `IRemoteCarCallback` 追加方法后，旧客户端 APK 仍能运行（收不到新回调），但用新 AAR 重新编译旧源码会失败 —— `Stub` 多了抽象方法。所以回调接口应尽早定型，或按需新建独立回调接口，不要长期往里塞方法。
- **真机联调**：`CarClientDemo` 已能构建，但当前环境没有连接设备，`bindService` 的成功路径未实跑过。
- **R8 验证**：本工程 Release 未开 `minifyEnabled`，`consumer-rules.pro` 只是随 AAR 下发，尚未在真实混淆下验证过绑定。
- **进程隔离**：现为同进程。要拆 `android:process=":car"`，必须先把 `CarMapApplication` 按进程名分支初始化，见下方说明。

### 构建注意

- **`buildFeatures { aidl = true }` 必须显式写**：AGP 8 起默认关闭，漏了不会报编译错，只是 AAR 里没有 `Stub`，外部调用方 `asInterface` 时才 `NoClassDefFoundError`。已验证模块级写法能覆盖 convention 插件。
- `car-api/consumer-rules.pro` 会以 `proguard.txt` 打进 AAR，调用方开启混淆后仍能正常绑定。
- 校验产物：`unzip -l car-api-release.aar`（应含 `classes.jar`、`proguard.txt`）；`unzip -l classes.jar | grep Stub` 应看到 `IRemoteCarService$Stub`。

### 兼容规则（一旦发布即对第三方生效）

1. 方法只能**追加在接口末尾**——AIDL 按声明顺序分配 transaction code，插在中间会让旧客户端参数错位；
2. 不删方法、不改参数类型与顺序；
3. Parcelable 字段只追加，不改顺序（`CarIdentity` 手写 `writeToParcel` 就是为了让这条规则在代码里显式可见）；
4. 业务失败用返回码表达，不用异常；`RemoteException` 只代表链路断了；
5. 回调接口必须 `oneway`，否则客户端一卡就反压服务端的 Binder 线程池；
6. 能力协商用 `getApiVersion()`，不要依赖 `getInterfaceVersion()`（那是 stable AIDL / HAL 的机制）。标准写法见 `CarClientDemo.ensureApiLevel()`：调用前判断，版本不够就降级，而不是硬调之后收一句无指向性的 `Transaction failed`。
7. `CarApi.API_VERSION` 是 `const val`，会被**内联**进 `:service` 的字节码——升版后必须重新编译/打包，否则 `getApiVersion()` 返回的仍是旧值。当前 `:service` 是 `:app` 的库模块，重打 APK 必然重编，暂时踩不到；若将来它独立发布 AAR，这条就是必检项。

### 外部方绑定（两个必须避开的坑）

- **不要用包名 `setPackage()`**：`applicationId` 按车型覆盖为 `com.sky.automap.h6` / `.h9`，且两个包可同时安装。正确做法是用 `CarApi.ACTION_SERVICE` 做 `queryIntentServices`，再用返回的组件名 bind；命中多个时用 `getIdentity()` 的 `carModel` / `channelId` 区分。
- Android 11+ 需在外部方 manifest 声明可见性，否则查不到 service：

```xml
<queries>
    <intent><action android:name="com.sky.automap.car.IRemoteCarService" /></intent>
</queries>
```

- 绑定前需持有 `com.sky.automap.permission.BIND_CAR_SERVICE`；签名不一致的第三方拿不到 `signature` 级权限，只能降级为运行时签名白名单校验（`getCallingUid()` → `getPackagesForUid()` → 比对签名 SHA-256）。
