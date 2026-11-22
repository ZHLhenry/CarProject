# ============================================================================
# car-api 对外契约的混淆规则（consumer-rules 会随 AAR 传给调用方工程）
#
# 1. AIDL 生成的 Stub / Proxy 靠接口全限定名做 Binder 描述符（DESCRIPTOR），
#    类名或包名被混淆后，跨进程两端认不出对方 → 必须 keep。
# 2. Parcelable 的 CREATOR 由 AIDL 生成的读写代码反射访问 → 必须 keep。
# 3. 本文件的规则对调用方生效，所以只能写「本 AAR 自己的类」，
#    不要写 -keep class com.sky.automap.map.** 这类会影响宿主的东西。
# ============================================================================

-keep class com.sky.automap.car.api.** { *; }
-keep interface com.sky.automap.car.api.** { *; }

# Parcelable 静态 CREATOR（自定义 Parcelable，未用 @Parcelize）
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
