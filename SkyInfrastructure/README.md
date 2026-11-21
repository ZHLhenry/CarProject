# SkyInfrastructure

基建层（独立 Gradle 工程，可单独用 Android Studio 打开、单独编译、单独发版）。

## 产物

- `infrastructure`：SkyMVVM / SkyWidget / SkyMultiStateLayout 统一出口 + 多状态布局约定（StateLayoutExt）

发布坐标：`groupId=com.sky.automap`、`artifactId=模块名`、`version=CarMapProject/versions.properties 里该 artifact 那一行`，
输出到上一级的本地仓库 `../CarMapProject/maven-repo`。

## 构建

```bash
./gradlew publish        # 发布本工程全部模块的 AAR
```

## 前置

无上游依赖，可最先构建。红线：本工程不得依赖任何 `map-*`、业务组件或 HMI 工程。

工程整体结构、依赖红线与新增车型步骤见上一级 `README.md`。
