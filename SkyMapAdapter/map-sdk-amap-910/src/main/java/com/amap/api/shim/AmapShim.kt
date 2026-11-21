package com.amap.api.shim

/**
 * 标记「某成员从哪个 SDK 版本开始存在」。
 *
 * 只有**最新版模块**（本模块，同时兼任 `map-impl-amap` 的编译基线）需要它：
 * 基线里同时摆着两个版本的 API，靠注解表达版本归属，供两处消费：
 * 1. 静态校验任务（`verifyMapSdkChain`）据此重建每个版本的 API 面；
 * 2. 人看代码时一眼知道这行是哪个版本才有的。
 *
 * `map-sdk-amap-850` 是「一版一模块」的真实 API 面，不需要注解。
 * 真实 aar 没有这个包，接入官方 SDK 时整份影子层替换掉即可。
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

/**
 * 标记「某成员在哪个 SDK 版本被移除」。
 *
 * 这类成员是继承链上唯一的风险来源：老版本写的适配代码如果调了它，
 * 而新版本 binding 没有覆写对应方法，运行期就是 `NoSuchMethodError`。
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
annotation class ApiRemoved(val value: String)

/**
 * 影子层的版本标定 + 运行期守卫。
 *
 * 本模块是 **910 的 API 面 + 编译基线**：为了让 850 两段适配代码也能编译，
 * 这里保留了它们在 910 上已被移除的成员，并用 [ApiRemoved] + [gate] 把
 * 「运行期才会炸的 NoSuchMethodError」提前成一条能读懂、带符号名的异常。
 *
 * [mode] 只有一个合法值（[MODULE_VERSION]）：壳工程注入了哪个版本的 aar，
 * 这里就必须是哪个版本。`AmapBindingFactory.of(...)` 写错版本时启动第一秒即失败，
 * 避免「装配器声明 850、包里却打进 910」这种静默错配。
 */
object AmapSdk {

    const val V850: String = "850"
    const val V910: String = "910"

    /** 本模块模拟的 SDK 版本 */
    const val MODULE_VERSION: String = V910

    /** 版本由低到高，[gate] 依赖这个顺序判断「有没有到这个版本」 */
    private val ORDER: List<String> = listOf(V850, V910)

    @Volatile
    var mode: String = MODULE_VERSION
        set(value) {
            require(value == MODULE_VERSION) {
                "本模块是 AMap $MODULE_VERSION 影子 SDK（兼编译基线），却被要求切换成 $value。" +
                    "版本由壳工程注入的 AAR 决定，请让 AmapBindingFactory.of(...) 与车型矩阵里的 sdk 保持一致。"
            }
            field = value
        }

    val currentLevel: Int get() = ORDER.indexOf(mode)

    fun levelOf(version: String): Int =
        ORDER.indexOf(version).also { require(it >= 0) { "未知的高德 SDK 版本：$version" } }

    /**
     * 运行期守卫：调用「当前版本没有的 API」立刻失败。
     *
     * @param since     该 API 从哪个版本开始有
     * @param removedIn 该 API 在哪个版本被移除（未移除传 null）
     * @param symbol    出错时要打印的符号名
     */
    fun gate(since: String, removedIn: String?, symbol: String) {
        val level = currentLevel
        if (level < levelOf(since)) {
            throw IllegalStateException("[$symbol] 需要 AMap ${since}+，当前模拟 ${mode}。缺这个能力应走 capabilities 降级。")
        }
        if (removedIn != null && level >= levelOf(removedIn)) {
            throw IllegalStateException(
                "[$symbol] 已在 AMap $removedIn 移除，当前模拟 ${mode}。" +
                    "这类调用必须放在被 `$removedIn` 绑定覆写的 open 方法内。",
            )
        }
    }
}
