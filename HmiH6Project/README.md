# HmiH6Project

H6 车型 HMI 工程（独立 Gradle 工程，可单独用 Android Studio 打开、单独编译、单独发版）。

## 产物

- `hmi-h6`：H6 的 HMI 入口、首页 Fragment 与导航图

发布坐标：`groupId=com.sky.automap`、`artifactId=模块名`、`version=CarMapProject/versions.properties 里该 artifact 那一行`，
输出到上一级的本地仓库 `../CarMapProject/maven-repo`。

## 资源三个维度：分辨率 / 主题 / 黑白夜

```
hmi-h6/src/main/res_1280x720/{layout,values,drawable,mipmap,mipmap-night}/     720 屏（1280x720）
hmi-h6/src/main/res_1920x1080/{layout,values,drawable,mipmap,mipmap-night}/    1080 屏（1920x1080）
hmi-h6/src/main/theme_blue/{values/colors.xml, values-night/colors.xml}        蓝色主题（白天 / 夜间）
hmi-h6/src/main/theme_red/{values/colors.xml, values-night/colors.xml}         红色主题（白天 / 夜间）
hmi-h6/src/main/res/{navigation,values,values-en,values-zh-rTW}/              与分辨率 / 主题都无关（含多语言，见下节）
```

| 维度 | 选择方式 | 说明 |
|---|---|---|
| 分辨率 | 构建期 `-Ph6.resolution=1280x720\|1920x1080` | 内层目录不带 `-1280x720` 限定符；同一时刻只挂一个目录，换分辨率需重新 publish |
| 主题 | 构建期 `-Ph6.theme=blue\|red` | Android 没有「主题限定符」，多套色板只能构建期二选一；布局只引用颜色名，换主题不改布局 |
| 黑白夜 | 运行时自动（`values-night`） | 标准 `-night` 限定符，系统 uiMode 切换，零代码；宿主题需是 DayNight 系（壳工程 `Theme.CarMap` 已是） |

```bash
./gradlew publish                                 # 1280x720 + blue（默认）
./gradlew publish -Ph6.resolution=1920x1080       # 1080 屏 + blue
./gradlew publish -Ph6.theme=red                  # 1280x720 + red
```

- 分辨率与主题目录都由 `hmi-h6/build.gradle.kts` 按属性挂进 `sourceSets`，非法值配置期直接失败：
  `h6.theme=green 不可用，可选：blue / red`。
- 颜色按**用途**命名（`h6_home_bg`、`h6_home_card_bg`、`h6_home_card_stroke`、
  `h6_home_title_color`、`h6_home_status_color`、`h6_home_action_text_color`），
  布局 / drawable 里只写 `@color/h6_home_*`，不写死色值；主题目录只管色板，
  分辨率目录管尺寸、布局结构、切图。
- **同维度目录之间必须资源名一一对应**：
  - 两个分辨率目录：布局控件 id 一致（`tv_model`、`tv_status`、`btn_open_poi`、`btn_open_navi`、`btn_open_route`）、
    `dimen` / `style` / 文件名一致；
  - 两个主题目录：`values/colors.xml` 与 `values-night/colors.xml` 的颜色名一致，只换色值，缺一个就运行期找不到。
- 切图：`mipmap/`（白天）与 `mipmap-night/`（夜间）各一张，按分辨率给不同像素
  （720 → 216px、1080 → 324px）。
- `mipmap` 只放**位图**（PNG/WebP）或 v26 自适应图标；XML `<vector>` 放 mipmap 会被
  Android Studio 判成错误（`<vector> XML file should be in "drawable", not "mipmap"`），矢量图放 `drawable-*`。
- 布局里不写死 dp/sp，统一走 `@dimen/h6_home_*`、`@style/H6HomeAction`、
  `@drawable/h6_home_card_bg`、`@mipmap/h6_home_logo`，各维度只替换这些资源本身。

## 多语言

多语言和分辨率 / 主题不同：走标准 locale 限定符，**运行时跟随系统语言自动切换**，
不是构建期二选一，所以放在与两个维度都无关的 `src/main/res` 下：

```
hmi-h6/src/main/res/values/strings.xml          默认（简体中文，同时是兜底）
hmi-h6/src/main/res/values-en/strings.xml       英文
hmi-h6/src/main/res/values-zh-rTW/strings.xml   繁体中文
```

- 新增语言：复制 `values/strings.xml` 到 `values-<locale>/`，只翻译 `value`，
  `name` 一个都不能少（缺项 lint 会报 `MissingTranslation`）。
- 布局、导航图、Kotlin 里**不允许出现面向用户的字面量**：文本用 `@string/h6_home_*`，
  代码里用 `getString(R.string.h6_home_*)`（`HomeH6ViewModel` 只接收成品文案，不拼字符串）。
- 当前覆盖：首页标题、logo 无障碍描述、状态占位符与就绪文案、三个入口按钮、导航图 label。
- 例外：`HmiH6Entry.displayName` 是 `hmi-api` 契约里的 `String`（壳工程用来当 Activity 标题），
  没有 Context 可查资源；要本地化需要改契约（如加 `@StringRes displayNameRes`），属于跨工程改动。

> **IDE 提示**：构建期二选一意味着 Gradle 模型里只登记「当前选中」的分辨率 / 主题目录，
> 未被选中的目录（如默认配置下的 `res_1920x1080`、`theme_red`）在 IDE 里会报
> `URI is not registered` / 资源无法解析——不影响构建（`assembleRelease`、`lint` 都正常）。
> 想改另一套资源时，要么切参数后 Sync 一次（IDE 模型跟着切），
> 要么在 IDE 里把该目录临时 Mark as Resources Root（仅本地，不改构建）。

## 构建

```bash
./gradlew publish        # 发布本工程全部模块的 AAR
```

## 前置

需先发布 `SkyHmiApi`、`SkyInfrastructure`、`SkyBusinessComponent`。与 HmiH9Project 完全独立，互不依赖。

工程整体结构、依赖红线与新增车型步骤见上一级 `README.md`。
