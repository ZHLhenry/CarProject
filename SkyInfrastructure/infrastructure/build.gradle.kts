plugins {
    alias(libs.plugins.sky.android.library.common)
}

// 对外发布 release 变体的软件组件（AAR + POM）
android {
    namespace = "com.sky.automap.infrastructure"

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // ---- Infrastructure 基建层核心：Sky 系列基础框架（对上层统一以 api 暴露） ----
    // SkyMVVM：网络 / MVVM 架构（BaseApplication、BaseVmActivity、BaseVmFragment、单 Activity 导航、Retrofit 封装）
    api(libs.skymvvm)
    // SkyWidget：通用 UI 控件（角标、底部导航、圆角图片、跑马灯、签名板、二维码）
    api(libs.skywidget)
    // SkyMultiStateLayout：多状态布局（加载中 / 空数据 / 错误 / 断网 / 自定义状态）
    api(libs.skymultistatelayout)

    // ---- 通用 UI 基础依赖（上层业务组件无需重复声明） ----
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.androidx.constraintlayout)
    api(libs.material)
}
