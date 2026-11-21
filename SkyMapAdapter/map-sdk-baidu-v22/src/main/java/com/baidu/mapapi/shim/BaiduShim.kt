package com.baidu.mapapi.shim

/**
 * 标记「某成员从哪个 SDK 版本开始存在」（百度侧）。
 *
 * 与高德侧同构：并集影子层把 v21 / v22 的 API 合到一个模块，
 * 版本归属写在注解上，供静态校验任务重建各版本 API 面。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FIELD,
)
annotation class ApiSince(val value: String)

/** 标记「某成员在哪个 SDK 版本被移除」（百度侧） */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FIELD,
)
annotation class ApiRemoved(val value: String)

/**
 * 百度影子层的版本标定 + 运行期守卫。
 *
 * 本模块是 **v22 的 API 面 + 编译基线**：为了让 v21 那段适配代码也能编译，
 * 这里保留了它在 v22 上已被移除的成员，并用 [ApiRemoved] + [gate] 把
 * 「运行期才会炸的 NoSuchMethodError」提前成一条能读懂、带符号名的异常。
 *
 * [mode] 只有一个合法值（[MODULE_VERSION]）：与壳工程注入的 aar 必须一致。
 */
object BaiduSdk {

    const val V21: String = "v21"
    const val V22: String = "v22"

    /** 本模块模拟的 SDK 版本 */
    const val MODULE_VERSION: String = V22

    private val ORDER: List<String> = listOf(V21, V22)

    @Volatile
    var mode: String = MODULE_VERSION
        set(value) {
            require(value == MODULE_VERSION) {
                "本模块是百度地图 $MODULE_VERSION 影子 SDK（兼编译基线），却被要求切换成 $value。" +
                    "版本由壳工程注入的 AAR 决定，请让 BaiduBindingFactory.of(...) 与车型矩阵里的 sdk 保持一致。"
            }
            field = value
        }

    val currentLevel: Int get() = ORDER.indexOf(mode)

    fun levelOf(version: String): Int =
        ORDER.indexOf(version).also { require(it >= 0) { "未知的百度地图版本：$version" } }

    fun gate(since: String, removedIn: String?, symbol: String) {
        val level = currentLevel
        if (level < levelOf(since)) {
            throw IllegalStateException("[$symbol] 需要百度地图 ${since}+，当前模拟 ${mode}。缺这个能力应走 capabilities 降级。")
        }
        if (removedIn != null && level >= levelOf(removedIn)) {
            throw IllegalStateException(
                "[$symbol] 已在百度地图 $removedIn 移除，当前模拟 ${mode}。" +
                    "这类调用必须放在被 `$removedIn` 绑定覆写的 open 方法内。",
            )
        }
    }
}
