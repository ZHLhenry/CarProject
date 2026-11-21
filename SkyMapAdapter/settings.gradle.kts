pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
        }
        // SkyBuildLogic（convention 插件）私有仓库
        maven {
            credentials {
                username = "677b4e5b259532263f6b30a6"
                password = "RnVrdxoghjKo"
            }
            url = uri("https://packages.aliyun.com/6732fc8f356ccaf8531a1487/maven/skybuildlogic")
            content {
                includeGroupByRegex("sky\\.android.*")
                includeGroupByRegex("com\\.sky.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ---- 本地 Maven 仓库（优先命中，跨工程 AAR 依赖都从这里取） ----
        maven {
            name = "skyLocalRepo"
            url = uri("${rootDir}/../CarMapProject/maven-repo")
        }

        google()
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            url = uri("https://jitpack.io")
        }
        // Sky 系列库（SkyMVVM / SkyWidget / SkyMultiStateLayout）私有仓库
        fun aliyunMaven(repoUrl: String) {
            maven {
                credentials {
                    username = "677b4e5b259532263f6b30a6"
                    password = "RnVrdxoghjKo"
                }
                url = uri(repoUrl)
            }
        }
        aliyunMaven("https://packages.aliyun.com/6732fc8f356ccaf8531a1487/maven/skymvvm")
        aliyunMaven("https://packages.aliyun.com/6732fc8f356ccaf8531a1487/maven/skywidget")
        aliyunMaven("https://packages.aliyun.com/6732fc8f356ccaf8531a1487/maven/skymultistatelayout")
    }
}

rootProject.name = "SkyMapAdapter"

// 契约层：厂商与版本都无关
include(":map-api")

// 门面：注入、取用、能力查询
include(":map-factory")

// 厂商实现层：厂商公共逻辑 + 该厂商的**全部版本适配**。
// 版本差异用包区分（binding.v850 / v910、binding.v21 / v22），不拆模块：
//   - 版本之间仍用继承串成链（850 → 910、v21 → v22）；
//   - 版本写错在编译期就失败（版本用枚举，不用字符串坐标）。
include(":map-impl-amap")
include(":map-impl-baidu")

// SDK 影子层：**一个版本一个模块**，与官方 aar 一一对应，壳工程按车型注入唯一一份。
// 例：h6 用高德 850 → implementation("com.sky.automap:map-sdk-amap-850")
//
// 最新版模块还兼任「编译基线」（map-impl-* 只 compileOnly 它一份）：
// 适配代码各段同处一个模块、共用一条 classpath，需要「多版 API 的并集」；
// 并集天然属于最新版（新版本 = 老版本 + 新增 − 移除），
// 那些仅剩历史意义的成员带 @ApiRemoved，运行期调用即抛。
//
// 真实接入官方 aar 时：把这 4 个模块整体换成官方坐标，一个模块对一份 aar。
include(":map-sdk-amap-850")
include(":map-sdk-amap-910")   // 910 的 API 面 + 编译基线
include(":map-sdk-baidu-v21")
include(":map-sdk-baidu-v22")  // v22 的 API 面 + 编译基线
