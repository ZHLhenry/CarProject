# CarClientDemo

**外部应用**调用 CarMap 的 AIDL 契约的最小可运行示例。

它扮演第三方 App：只依赖一个 Maven 坐标 `com.sky.automap:car-api` 的 AAR，
不依赖仓库内任何工程源码，也不用 SkyBuildLogic 插件。整个工程就是一个可交付的样本。

## 前置

1. 契约 AAR 已发布到本地仓库（在 `SkyHmiApi/` 下执行）：

   ```bash
   ./gradlew :car-api:publish
   # 产物：../CarMapProject/maven-repo/com/sky/automap/car-api/1.0.0/
   ```

2. 车机应用已安装，**且是 debug 包**（在 `CarMapProject/` 下执行）：

   ```bash
   ./gradlew :app:installDefault
   ```

   为什么强调 debug：对端声明的 `com.sky.automap.permission.BIND_CAR_SERVICE` 是
   `signature|privileged` 级别，只有**同签名**的应用才拿得到。两边都打 debug 包时，
   同一台机器共用 `~/.android/debug.keystore`，天然同签名，直接能绑上。
   对端换成 release 包（自己的 keystore）后，本 demo 也必须用同一个 keystore 重签。

## 构建 / 安装

```bash
./gradlew :app:installDebug
# 或只出包：./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 操作与预期日志

打开应用后按顺序点：

| 操作 | 预期日志 |
|---|---|
| 检查权限 | `权限 com.sky.automap.permission.BIND_CAR_SERVICE 已授予`（未授予说明签名不一致） |
| 查找并绑定 | `queryIntentServices(...) → 1 个实现` → `发现 com.sky.automap.h6 / ...RemoteCarService` → `bindService → true` |
| （自动） | `onServiceConnected` → `getApiVersion() = 1` → `getIdentity(): applicationId=com.sky.automap.h6, carModel=H6, ...` → `回调 onServiceReady` |
| 查询身份 | 再打一遍 version 与 identity |
| 注册回调 | `registerCallback 已提交`，若已就绪立刻回一次 `onServiceReady` |
| 解绑 | 先 `unregisterCallback` 再 `unbindService` |
| 搜索 POI | `searchPoi("充电站") → N 条` + 前 3 条明细 |
| 规划路线 | `planRoute → N 个途经点（首点=起点，末点=终点）` |
| 导航到首个 | `navigateTo(xxx) → result=0（成功）`，随后收到 `onNavStateChanged` |
| 停止导航 | `onNavStateChanged` 推送回空闲态 |
| 查导航态 | `getNavState(): CarNavState(navigating=..., remaining=...m, eta=...s)` |

业务方法（API 2）注意两点：

- **空列表 ≠ 出错**。`searchPoi` / `planRoute` 失败与「没结果」都返回空列表，
  真要区分得看服务端日志或后续版本加错误码（契约演进只能追加）。
- **返回码要分段处理**：`navigateTo` 返回 `CarApi.RESULT_*`，
  `201`（未就绪）值得重试，`101`（参数错误）重试也没用。`describe()` 里有对照。

### 为什么要先问 `getApiVersion()`

调 API 2 的方法前，demo 都会先走 `ensureApiLevel(2, "...")`。对端若是旧版应用
（只到 API 1），日志会是：

```
搜索 POI 需要 API 2，对端只到 API 1 → 降级：跳过本次调用
```

而不是让调用真的发出去、然后收到一句 `RemoteException: Transaction failed`。

原因在 AIDL 的机制里：transaction code 按**方法声明顺序**分配。旧服务端的
`Stub.onTransact` 不认识新的 code，落到 `default` 返回 `false`，客户端只能拿到一个
既没说是「版本太老」也没说是「哪个方法」的失败。版本协商是唯一能在**调用前**
避开它的手段，所以它是契约里唯一一个「不自述能力就没法演进」的方法。

查不到 service 时，日志会直接列出三个排查点，见 `MainActivity.findAndBind()`。

## 五个必须踩对的点

1. **用 action 查，不要写死包名。** 对端 `applicationId` 按车型是
   `com.sky.automap.h6` / `.h9`，且两个包可以同时安装 —— 写死包名必然有一半车型绑不上。
   查完用返回的 `ComponentName` 去 bind。
2. **manifest 必须有 `<queries>`。** Android 11+ 不声明的话 `queryIntentServices()`
   返回空列表，且**不报错**，这是「明明装了却查不到」的头号原因。
3. **签名要对得上。** `signature` 级权限下，签名不一致时 `bindService` 被系统拒绝，
   logcat 里是 `Permission Denial ... requires com.sky.automap.permission.BIND_CAR_SERVICE`。
4. **`bindService` 返回 true ≠ 业务就绪。** 那只代表绑定请求被受理，
   服务端的地图 SDK / 基建层可能还在初始化。以 `onServiceReady` 为准。
5. **必须收尾。** 回调要 `unregisterCallback`、连接要 `unbindService`、
   代理要 `linkToDeath`。少一个就会在服务端崩溃后拿着僵尸代理继续调用，
   或者服务端一直持有客户端 binder 引用。

## 移植到真实第三方工程时要改的地方

只有两处：

- `settings.gradle.kts` 的 `carMapLocalRepo` 地址 → 换成对方的私服（或用 `mavenLocal` 联调）；
- `gradle/libs.versions.toml` 的 `carApi` → 锁到对方指定的版本。

`MainActivity` 与 `AndroidManifest.xml` 里的绑定逻辑可以直接照抄。

## 文件

```
app/src/main/
├── AndroidManifest.xml                 uses-permission + <queries> + 入口 Activity
└── java/com/sky/demo/carclient/
    └── MainActivity.java               查找 / 绑定 / 调用 / 回调 / 死亡监听 / 解绑
```

用 Java 而不是 Kotlin：AIDL 生成的 `Stub` 本身就是 Java，外部方用 Java 工程的场景很多；
同时这也让本工程不依赖 Kotlin 插件，构建脚本只剩 AGP 一个插件。
