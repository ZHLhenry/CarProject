# HmiH9Project

H9 车型 HMI 工程（独立 Gradle 工程，可单独用 Android Studio 打开、单独编译、单独发版）。

## 产物

- `hmi-h9`：H9 的 HMI 入口、首页 Fragment 与导航图

发布坐标：`groupId=com.sky.automap`、`artifactId=模块名`、`version=CarMapProject/versions.properties 里该 artifact 那一行`，
输出到上一级的本地仓库 `../CarMapProject/maven-repo`。

## 构建

```bash
./gradlew publish        # 发布本工程全部模块的 AAR
```

## 前置

需先发布 `SkyHmiApi`、`SkyInfrastructure`、`SkyBusinessComponent`。与 HmiH6Project 完全独立，互不依赖。

工程整体结构、依赖红线与新增车型步骤见上一级 `README.md`。
