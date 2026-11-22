plugins {
    alias(libs.plugins.sky.android.library.common)
}

android {
    namespace = "com.sky.automap.car.api"

    // AGP 8 起 aidl 默认关闭（本仓库 AGP 9.5），必须显式打开：
    // 否则 src/main/aidl 不参与编译，AAR 里没有 IRemoteCarService$Stub，
    // 外部调用方 asInterface 时直接 NoClassDefFoundError。
    buildFeatures {
        aidl = true
    }

    defaultConfig {
        // AIDL Stub / Parcelable CREATOR 在调用方混淆时必须保留，规则随 AAR 下发
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // 对外契约层：除 Android framework 外零依赖。
    // 不引 androidx / 不吃 api 依赖 —— 任何一条都会把版本约束传染给外部调用方，
    // 而外部方不一定和本仓库同版本，冲突时只能由他们 exclude，属于设计缺陷。
    //
    // 注意 POM 里会出现一条 kotlin-stdlib（compile scope）：数据契约（CarIdentity /
    // CarPoi / CarLatLng / CarNavState）与常量类 CarApi 都是 Kotlin 写的，运行期需要
    // Intrinsics，这条由 Kotlin 插件自动注入，不要手写版本。
    // 若将来把 DTO 改成 Java，这条依赖会自然消失，届时 POM 才是真空的。
}
