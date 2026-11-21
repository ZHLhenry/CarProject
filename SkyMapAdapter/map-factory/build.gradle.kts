plugins {
    alias(libs.plugins.sky.android.library.common)
}

android {
    namespace = "com.sky.automap.map.factory"

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // 工厂层只依赖接口层，绝不依赖任何具体实现（实现由壳工程注入）
    api(project(":map-api"))
}
