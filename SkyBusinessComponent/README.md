# SkyBusinessComponent

业务组件层（独立 Gradle 工程，可单独用 Android Studio 打开、单独编译、单独发版）。

## 产物：**一个 AAR**

| 模块 | 坐标 | 内容 |
|---|---|---|
| `business` | `com.sky.automap:business` | POI 检索 / 导航引导 / 路线规划（各带 Fragment + ViewModel + 子导航图） |

内部按业务**分包**，不再拆模块：

```
business/src/main/java/com/sky/automap/component
├── R.kt（AGP 生成，namespace = com.sky.automap.component）
├── poi/         PoiFragment  / PoiViewModel  / res/navigation/poi_graph.xml
├── navigation/  NaviFragment / NaviViewModel / res/navigation/navi_graph.xml
└── route/       RouteFragment/ RouteViewModel/ res/navigation/route_graph.xml
```

三张子导航图由 HMI 工程 `<include app:graph="@navigation/poi_graph" />` 之类的写法组合。

### 为什么合成一个 AAR

- **依赖完全相同**：三者都是 `infrastructure + map-api`（契约 + 门面同一个 AAR），拆开只是多两次 AAR 装配；
- **天然一起用**：HMI 的导航图同时 include 三张子图，拆开后下游必须一次引三个坐标，
  少引一个不是编译错误而是运行期 `destination not found`；
- **一起演进**：业务组件同属一层，没有必要各自独立版本号。

保留的是**包边界**而不是构建边界——跨包引用仍然显式，但发布、版本、依赖声明收敛成一份。
若以后某个组件需要独立发版（例如单独给某个车型定制），再把它从包里升级为模块即可，调用方只改 import。

发布坐标：`groupId=com.sky.automap`、`artifactId=business`、`version=CarMapProject/versions.properties 里该 artifact 那一行`，
输出到上一级的本地仓库 `../CarMapProject/maven-repo`。

## 依赖约定（严格分法）

```kotlin
api("com.sky.automap:infrastructure:${skyVersionOf("infrastructure")}")   // 基类出现在公开签名中 → api
api("com.sky.automap:map-api:${skyVersionOf("map-api")}")          // 契约（map.api 包）+ 门面（map.factory 包）
```

- 组件内**不出现** `MapProviderRegistry`、`AmapMapProvider`、`BaiduMapProvider`；
- 地图调用统一走 `MapManager.createMapView(...)` / `MapManager.searchPoi(...)` / `MapManager.planRoute(...)`；
- 因此换地图厂商时，本工程一行代码都不用改。

## 构建

```bash
./gradlew publish        # 发布 business.aar（一个模块）
```

## 前置

需先发布 `SkyInfrastructure` 与 `SkyMapAdapter`（本工程以 Maven 坐标依赖 `infrastructure`、`map-api`）。

工程整体结构、依赖红线与新增车型步骤见上一级 `README.md`。
