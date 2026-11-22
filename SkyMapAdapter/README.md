# SkyMapAdapter

地图适配层（独立 Gradle 工程，可单独用 Android Studio 打开、单独编译、单独发版）。

## 一、两个关键问题

**Q1：910 要复制一遍 850 的功能吗？**
不要。版本之间用**继承串成链**，每个版本只写「相对上一版发生了什么变化」：

```
binding.v850.Amap850Binding        公共适配代码的唯一一份（初始化 / 建图+路况 / 检索 / 路线 / 标注 / 生命周期）
     ↑
binding.v910.Amap910Binding        只写改动：隐私合规、构造参数、同步检索、多路线（~80 行）
```

> 高德 650 已不再支持（没有车型在用），链路从 850 起，850 因此是基类。

百度侧同理：`binding.v21.BaiduV21Binding`（基类）← `binding.v22.BaiduV22Binding`（只写 4 处变化）。

判断标准很简单——**「未变动的功能是否还能调用到 850 的代码」**：源码层面靠 `open` + `override` 天然成立；
字节码层面要求「同一个 APK 只装一份 SDK」，由此得到一条必须记住的推论：

> 版本适配代码是「按老 SDK 编译、在新 SDK 上跑」的，所以**可能出现被移除 API 的调用**。
> 这类调用只能待在「新版本一定会覆写的 `open` 方法」里。

**Q2：模块怎么分？**
两条规则，一条按 SDK，一条按代码：

| | 怎么分 | 为什么 |
|---|---|---|
| **SDK** | **一个版本一个模块**：`map-sdk-amap-850 / -910`、`map-sdk-baidu-v21 / -v22` | 与官方 aar 一一对应：真实接入时一个模块换一个坐标；壳工程按车型注入，打进包的永远只有一份 |
| **适配代码** | **一个厂商一个模块 + 包区分版本**：`map-impl-amap` 里的 `binding.v850 / v910` | 两个版本是继承链（`class Amap910Binding : Amap850Binding()`），必须同处一个编译单元才能互相继承；拆模块就得再拆出 `map-binding-*` 那一层，而适配代码只有 30~120 行/版本，不值一份 build 脚本 |

代价是「编译器不再按版本隔离 API」，用两道更轻的机制补上（见第五节）。

## 二、产物（7 个模块，分三层）

| 层 | 模块 | 职责 |
|---|---|---|
| 契约 + 门面层 | `map-api` | 一个 AAR 两个包：`map.api` 是纯契约（`IMapProvider`、`IMapView`、`MapCapabilities`、`LatLng` / `MapMarker` / `MapPoi`、`MapSdkType`，零可变状态）；`map.factory` 是门面（`MapManager` 注入 / 取用 / `capabilities()`，注册表 `MapProviderRegistry` 为 `internal`） |
| 实现层 | `map-impl-amap` / `map-impl-baidu` | **厂商公共层 + 该厂商全部版本适配**。公共层不 import 任何厂商 SDK；版本适配在 `binding.v*` 包里 |
| SDK 层 | `map-sdk-amap-850` / `-910`、`map-sdk-baidu-v21` / `-v22` | 影子 SDK，**一个模块 = 一份 aar 的 API 面**；最新版模块兼任编译基线 |

> 门面没有单独成模块：它只有两个文件（`MapManager` + `MapProviderRegistry`），而业务组件与壳工程本来就要依赖 `map-api` 拿数据模型，拆开只多一个坐标、一次发布、一行版本表。
> 代价是「契约层零可变状态」由包边界保证而非模块边界——`map.factory` 包是唯一允许持有可变状态的地方。

`map-impl-amap` 内部的包就是分层：

```
com.sky.automap.map.impl.amap              ← 厂商公共层：AmapMapProvider / AmapMapView（不 import com.amap.api.*）
com.sky.automap.map.impl.amap.spi          ← AmapBinding / AmapNativeMap / RawPoi（SPI）
com.sky.automap.map.impl.amap.binding      ← AmapVersion（枚举）+ AmapBindingFactory（版本 → binding）
com.sky.automap.map.impl.amap.binding.v850 ← 继承链基类（公共适配代码）
com.sky.automap.map.impl.amap.binding.v910 ← 只写改动
```

发布坐标：`groupId=com.sky.automap`、`artifactId=模块名`、`version=CarMapProject/versions.properties 里该 artifact 那一行`，
输出到上一级的本地仓库 `../CarMapProject/maven-repo`。

## 三、链路结构

```
业务组件（business.aar：poi / navigation / route 三个包）
        │ 只依赖 map-api（契约 + 门面，一个坐标）
   MapManager（map-api 的 map.factory 包）
        │
    map.api 包（纯契约，业务层只按 MapCapabilities 降级）
        │
  map-impl-amap ──────────────────────────────────┐
        ├── 厂商公共层（版本无关）  ← AmapBinding SPI
        └── binding.v850 / v910（继承链，一个包一环）
        │
        │ compileOnly（拿类型，不进 POM、不进包）
        ▼
  map-sdk-amap-910   ← 910 的 API 面 + 两版并集（编译基线）

  壳工程按渠道注入唯一一份（真正进 dex 的那一份）
        ..._amap850 → map-sdk-amap-850    ..._amap910 → map-sdk-amap-910
        H9 同理：..._baiduv21 → map-sdk-baidu-v21，..._baiduv22 → -v22
```

注意最后一跳：**包是链，进去的 SDK 只有一个版本**。打 850 的渠道（`*_amap850`）dex 里只有 850 的 API 类
（`AMapOptions`、`updatePrivacyShow` 这些 910 才有的类不存在）；`*_amap910` 渠道反之。

版本只有一个来源（`CarMapProject/app-builds.json` 的 `carModels.<车型>.mapVersions` + 渠道的 `mapVersion`），
落到两处，互相校验：

```kotlin
// CarMapProject/app/build.gradle.kts —— 按渠道打进哪一份 aar
add("${build.id}Implementation", "com.sky.automap:map-sdk-${厂商}-${build.mapVersion}:...")

// CarMapProject/app/src/<车型>/.../PlatformAssembler.kt —— 用哪一段适配
AmapMapProvider(AmapBindingFactory.of(AmapVersion.of(BuildConfig.APP_MAP_VERSION)))
```

渠道里写错版本号在**配置阶段**就被 `carModels.mapVersions` 挡下；`AmapVersion` 是枚举，
写错枚举名编译期失败；两处对不上时，影子 SDK 的版本标定会在启动第一秒抛异常——
`AmapSdk.mode` 只接受本模块的版本，不会出现「声明 850、包里却是 910」的静默错配。

## 四、三类差异怎么处理

| 差异类型 | 例子 | 写法 | 基类是否改动 |
|---|---|---|---|
| 纯新增能力 | 850 加路况、910 加 3D/离线 | 覆写 `capabilities` 打能力位；需要初始化时覆写 `createNativeMap` 或原生地图的 `onCreate` | 不改 |
| 方法签名/返回形态变了 | 910 检索从异步改同步、POI 字段改名 | 覆写 `searchPoi`，新版本自己映射 | 不改 |
| API 被移除 | 910 去掉 `MapView(context)`、去掉 `searchPOIAsyn` | **必须**覆写对应方法（`createNativeMap` / `searchPoi` / `planRoutes`），否则运行期 `NoSuchMethodError` | 不改，但基类必须 `open` |
| 签名没变、语义变了 | 910 的 `initialize` 必须先声明隐私 | 覆写该方法（静态检查抓不到，靠 `semanticBreaks` 声明 + 冒烟测试） | 不改 |

反向要求：基类里**不许**把这类调用写进构造函数、`init` 块、`val` 初始化式，也不许写进 `final` 方法——
一旦写进去，子类就没有覆写点，链就断了。本工程的 `Amap850NativeMap` 因此把 `map` 声明为 `protected val`、
把建图动作放在 `createNativeMap`（可覆写）而不是构造函数里。

### 覆写的两种语义：增量 vs 替换

子类能覆写父类的 open 方法（Kotlin 里 `override` 成员默认仍是 open，要封死得写 `final override`），
但**覆写里调不调 `super` 是两件完全不同的事**，判断依据是「父类方法体里有没有被本版本移除的 API」：

| | 父类方法体干净（只做通用逻辑） | 父类方法体里有被移除的 API |
|---|---|---|
| 该调 `super` 吗 | **要**，先复用再补差异 | **绝对不要**，必须整段替换 |
| 本工程实例 | `Amap910Binding.capabilities` → `super.capabilities.copy(4 个能力位)` | `Amap910Binding.searchPoi` / `planRoutes` / `createNativeMap` → 直接写新 API，不碰 `super` |
| 不这么做的后果 | 复制父类逻辑，850 加能力位时 910 不生效 | 一调 `super` 就回到老实现，`NoSuchMethodError` 照旧 |

`super.capabilities.copy(...)` 而不是 `MapCapabilities(...)` 全量重建，也是同一个道理：能力位以后新增时，
子类自动带上。注意 `verifyMapSdkChain` 只检查「对厂商 SDK 成员的引用位置」，**不校验**
`MapCapabilities` 的构造方式 —— 这一条靠代码评审。

## 五、编译基线 + 两道防线

适配代码两段同处 `map-impl-amap`，共用一条编译 classpath，而 850 段要调 `MapView(context)`、
910 段要调 `MapView(context, AMapOptions)`——**没有任何一份单版本 aar 能让这两段同时编译**。
所以 classpath 上必须是一份「两版 API 的并集」。

并集不单独开一个「只用于编译」的模块，而是**放在最新版模块里**（`map-sdk-amap-910` / `map-sdk-baidu-v22`）：

```kotlin
// map-sdk-amap-910：910 的 API 面 + 仅剩历史意义的成员
class MapView private constructor(context: Context, options: AMapOptions, applyOptions: Boolean) {
    @ApiSince(AmapSdk.V910)   constructor(context: Context, options: AMapOptions) : ...
    @ApiRemoved(AmapSdk.V910) constructor(context: Context) : ...   // 850 有，910 没有
}
```

理由：并集天然属于最新版（新版本 = 老版本 + 新增 − 移除）；`map-sdk-amap-850`
保持干净的单版本 API 面，不带任何历史成员。带 `@ApiRemoved` 的成员被调用时直接抛异常
（语义与真机一致，只是错误信息更清楚），同时被静态检查当作「必须被覆写」的判据。

两道防线：

**防线一（构建期，CI 必跑）：`verifyMapSdkChain`**

```bash
./gradlew verifyMapSdkChain
```

规则：对每个厂商的继承链，凡是被某版本移除（或改签名）的成员，其调用点必须落在
**该版本 binding 覆写过、且覆写不再 `super.` 调回父类的方法**里。违规分三种处理：
- 不可覆写的位置（构造函数 / `init` / `val` 初始化式 / `final` 方法）→ 直接失败，因为无解；
- 可覆写但没覆写 → 直接失败，因为运行时一定崩；
- 覆写了但里面还有 `super.同名方法()`（或中间某代又 super 回去）→ 失败：覆写形同虚设，
  运行期绕一圈仍执行到老实现。判据是字节码里的 `invokespecial`，会跳过「没实现该方法的中间版本」
  继续向上找真正的实现，所以「910 覆写 → 中间某版没覆写 → 更上层才是实现」这种路径也能抓到。

报告落在 `build/reports/sdk-chain/{amap,baidu}.md`。

**防线二（运行期）：`AmapSdk.gate`**

漏网的调用不会静默产生错误数据，而是在第一秒抛异常，并且异常信息直接给出符号名与版本：

```
IllegalStateException: [PoiSearch.searchPOIAsyn] 已在 AMap 910 移除，当前模拟 910。
这类调用必须放在被 `910` 绑定覆写的 open 方法内。
```

**为什么 SDK 依赖必须是 `compileOnly`**：链式继承要求「运行时只有一份 SDK」。写成 `implementation`
会随 AAR 传递，多个版本的 SDK 一起进包 → duplicate class。所以：

```kotlin
// map-impl-amap/build.gradle.kts
dependencies {
    api(project(":map-api"))
    compileOnly(project(":map-sdk-amap-910"))   // 只拿编译期类型，不进 POM、不进包
}

// CarMapProject/app/build.gradle.kts —— 唯一真正打包 SDK 的地方（按渠道表逐 flavor 注入）
appBuilds.values.forEach { build ->
    add("${build.id}Implementation", "com.sky.automap:map-impl-${厂商}:...")
    add("${build.id}Implementation", "com.sky.automap:map-sdk-${厂商}-${build.mapVersion}:...")
}
```

真实接入时一个模块换一个官方坐标：`compileOnly("com.amap.api:3dmap:9.1.0")` +
壳工程按渠道注入 `"<渠道ID>Implementation"("com.amap.api:3dmap:8.5.0")`，结构与写法不变；
同时删掉 `AmapBindingFactory.of()` 里的 `AmapSdk.mode = version.code` 一行（真实 aar 没有这个类）。

## 六、这条链的运行期风险与取舍

**1. 被移除的 API 留在未覆写的方法里 → `NoSuchMethodError`**
编译期发现不了（包内各段代码是同一份并集编译的）。已实测：H9 的 dex 里仍能看到 v21 绑定
`searchInCity` 的方法引用，只是 v22 覆写了 `searchPoi`，死代码永远不会被调到。三道防线：

1. `verifyMapSdkChain`（构建期静态检查，CI 必跑）；
2. 影子层 `gate`（开发期把崩溃提前到第一秒，信息可读）；
3. 冒烟测试：每个车型包至少在真机/模拟器跑「初始化 → 建图 → 检索 → 路线 → 打点」五步。

**2. 适配 AAR 里有「不属于本车型版本」的代码**
`map-impl-amap` 一个 AAR 装着 850/910 两段。打 850 的渠道（`*_amap850`）dex 里
`Amap910Binding` 引用的 `AMapOptions` 是**悬空引用**（`*_amap910` 渠道反之，850 段的 `MapView(context)` 悬空）
——但它只在 `AmapBindingFactory.of(V910)` 分支执行时才会被解析，850 渠道永远走不到。这是「适配按包分版本」的必要代价：
真按版本拆 AAR 就不会有悬空引用，代价是要再拆出 `map-binding-*` 一层（模块数 7 → 11）。

**3. 影子 SDK 之间有重复的桩代码**
`map-sdk-amap-850` / `-910`、`map-sdk-baidu-v21` / `-v22` 各自维护一份 API 面，改桩要动多处。
它们只是「手上还没有 aar 时的占位」：真实接入后这 4 个模块整体删掉，重复自然消失。

## 七、什么时候不要用继承

- 版本之间差异超过约 30%，或新版本是「另一套 API」而不是「上一版的演化」→ 直接独立实现，别硬套继承；
- 版本线分叉（9.1.0 与 9.1.1 各自长期维护、又都有车型在用）→ 继承链会变成树，改用组合（把公共能力抽成
  `AmapCommonApi`，各版本持有它）比继承更清楚；
- 车厂定制 SDK（改过类名/包名）→ 视为新厂商，单独一条 chain，不要塞进原厂商链。

## 八、新增一个 SDK 版本（如高德 920）

1. 新建模块 `map-sdk-amap-920`：从 `-910` 复制，改 `AmapSdk.MODULE_VERSION = "920"`；
   920 新增的成员标 `@ApiSince(V920)`，920 移除的成员标 `@ApiRemoved(V920)`——它成为**新的编译基线**；
2. 老模块（850 / 910）的 API 面**保持不动**，它们代表各自版本的真相；
3. 新建 `binding/v920/Amap920Binding : Amap910Binding()`（继承**最近的**上一版，不要继承 850）；
4. 只覆写改动项：能力位 `copy(...)`、被改的 API；没变的一律不写；
5. `AmapVersion` 加 `V920("920")`，`AmapBindingFactory.of()` 加一行 `when` 分支；
6. `settings.gradle.kts` 加 `include(":map-sdk-amap-920")`；`map-impl-amap` 的 `compileOnly`
   换成新基线；基线模块 `AmapSdk.ORDER` 加 `V920`；
7. `./gradlew verifyMapSdkChain` 必须绿，然后 `./gradlew publish`；
8. 壳工程：`app-builds.json` 的 `carModels.<车型>.mapVersions` 加 `"920"`、`versions.properties` 加
   `map-sdk-amap-920`（各工程构建时直接读这份表，无需同步）；装配器不用改（版本走 `BuildConfig.APP_MAP_VERSION`）。

厂商层、业务组件、HMI 工程**一行都不用改**。

## 九、构建

```bash
./gradlew verifyMapSdkChain   # 静态检查继承链（CI 必跑）
./gradlew publish             # 发布本工程全部 7 个模块的 AAR
```

## 十、前置

无上游依赖（本工程内部通过 `project(...)` 依赖，发布时自动映射为 `com.sky.automap:*` 坐标）。
工程整体结构、依赖红线与新增车型步骤见上一级 `README.md`。
