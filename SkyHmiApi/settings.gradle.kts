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
            // 凭据从 local.properties 读（该文件不入库）；未配置则匿名访问
            val skyProps = java.util.Properties()
            java.io.File(settingsDir, "local.properties").takeIf { it.isFile }
                ?.inputStream()?.use { skyProps.load(it) }
            val skyUser = skyProps.getProperty("skyRepo.username")
            val skyPass = skyProps.getProperty("skyRepo.password")
            if (skyUser != null && skyPass != null) {
                credentials {
                    username = skyUser
                    password = skyPass
                }
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
                // 凭据从 local.properties 读（该文件不入库）；未配置则匿名访问
                val skyProps = java.util.Properties()
                java.io.File(settingsDir, "local.properties").takeIf { it.isFile }
                    ?.inputStream()?.use { skyProps.load(it) }
                val skyUser = skyProps.getProperty("skyRepo.username")
                val skyPass = skyProps.getProperty("skyRepo.password")
                if (skyUser != null && skyPass != null) {
                    credentials {
                        username = skyUser
                        password = skyPass
                    }
                }
                url = uri(repoUrl)
            }
        }
        aliyunMaven("https://packages.aliyun.com/6732fc8f356ccaf8531a1487/maven/skymvvm")
        aliyunMaven("https://packages.aliyun.com/6732fc8f356ccaf8531a1487/maven/skywidget")
        aliyunMaven("https://packages.aliyun.com/6732fc8f356ccaf8531a1487/maven/skymultistatelayout")
    }
}

rootProject.name = "SkyHmiApi"

// hmi-api：对内「车型 HMI 插件」契约（同进程、编译期二选一）
// car-api：对外「跨进程 AIDL」契约（给第三方应用，要求二进制兼容）
// 两者都是纯契约 AAR，但稳定性等级与发布节奏不同，所以分模块、分 artifactId。
include(":hmi-api")
include(":car-api")
