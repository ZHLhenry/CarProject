// ============================================================================
// CarClientDemo —— 「外部应用」视角的 AIDL 调用演示（独立工程）
//
// 它与 CarMapProject 的关系只有一条：从对方发布的本地仓库里取
// com.sky.automap:car-api 这个 AAR。**不**依赖仓库内任何 project、
// **不**依赖 SkyBuildLogic convention 插件 —— 第三方拿到的就是这么一个工程。
// ============================================================================
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // 国内网络镜像（外部方可以删掉这几行，不影响功能）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ---- 关键：CarMapProject 发布的 AAR 在它的 maven-repo 目录里 ----
        // 真实第三方换成私服地址即可（settings 里只改这一处）
        maven {
            name = "carMapLocalRepo"
            url = uri("${rootDir}/../CarMapProject/maven-repo")
        }
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

rootProject.name = "CarClientDemo"

include(":app")
