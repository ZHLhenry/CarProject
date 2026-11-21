# SkyHmiApi

HMI 契约层（独立 Gradle 工程，可单独用 Android Studio 打开、单独编译、单独发版）。

## 产物

- `hmi-api`：`HmiEntry`（车型 HMI 与壳工程的唯一约定）

发布坐标：`groupId=com.sky.automap`、`artifactId=模块名`、`version=CarMapProject/versions.properties 里该 artifact 那一行`，
输出到上一级的本地仓库 `../CarMapProject/maven-repo`。

## 构建

```bash
./gradlew publish        # 发布本工程全部模块的 AAR
```

## 前置

无上游依赖，与 SkyInfrastructure 同级，可随时构建。

工程整体结构、依赖红线与新增车型步骤见上一级 `README.md`。
