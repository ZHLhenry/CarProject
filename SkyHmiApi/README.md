# SkyHmiApi

HMI 契约层（独立 Gradle 工程，可单独用 Android Studio 打开、单独编译、单独发版）。

## 产物

两个模块都是**纯契约 AAR**，但受众不同，所以分 artifactId、分版本：

- `hmi-api`：`HmiEntry`（车型 HMI 与壳工程的唯一约定）。同上进程、编译期二选一，只给本仓库内部用。
- `car-api`：**对外 AIDL 契约**（跨进程、给第三方应用）。当前接口能力为 API 2（AAR 坐标仍是 1.0.0）：
  - `IRemoteCarService`
    - API 1：`getApiVersion` / `getIdentity` / `registerCallback` / `unregisterCallback`
    - API 2：`searchPoi` / `planRoute` / `navigateTo` / `stopNavigation` / `getNavState`
  - `IRemoteCarCallback`（`oneway`）：`onServiceReady` / `onNavStateChanged`
  - Parcelable 数据契约：`CarIdentity`（包名/车型/渠道/厂商）、`CarPoi`、`CarLatLng`、`CarNavState`
  - `CarApi`（action、绑定权限名、`API_VERSION`、`RESULT_*` 返回码）

  只有契约没有服务端实现——实现放在壳工程的 `:service` 模块（尚未建立），
  见上一级 `README.md` 第十一节。外部方直接依赖本 AAR 即可拿到 `Stub` / `Proxy`，
  不需要 `.aidl` 源文件。

`car-api` 的两个构建要点：

1. `buildFeatures { aidl = true }` 必须显式声明——AGP 8 起默认关闭，漏了不报错、
   只是 AAR 里没有 `Stub`；
2. `consumer-rules.pro` 随 AAR 下发（打包为 `proguard.txt`），保证调用方混淆后仍能绑定。

发布坐标：`groupId=com.sky.automap`、`artifactId=模块名`、`version=CarMapProject/versions.properties 里该 artifact 那一行`，
输出到上一级的本地仓库 `../CarMapProject/maven-repo`。

## 构建

```bash
./gradlew publish        # 发布本工程全部模块的 AAR
```

## 前置

无上游依赖，与 SkyInfrastructure 同级，可随时构建。

工程整体结构、依赖红线与新增车型步骤见上一级 `README.md`。
