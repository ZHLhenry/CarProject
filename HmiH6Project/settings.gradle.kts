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

rootProject.name = "HmiH6Project"

include(":hmi-h6")
