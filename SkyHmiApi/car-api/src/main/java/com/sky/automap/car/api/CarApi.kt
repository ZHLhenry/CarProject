package com.sky.automap.car.api

/**
 * 对外 AIDL 契约的常量表——调用方引用这一个类就能完成绑定、能力判断与结果处理。
 *
 * 放在 AAR 里而不是只写进文档，是为了避免「文档里的 action 拼错一个字母」
 * 这类只能靠抓日志定位的问题。
 */
object CarApi {

    /**
     * 接口能力版本。
     *
     * 与 AAR 的 Maven 版本**无关**：
     *   · Maven 版本（versions.properties 里 car-api 那行）是发布标签，可能因为
     *     发版策略停在 1.0.0 却装着 API 2 的全部方法；
     *   · 本常量才是「对端到底会不会某个方法」的唯一依据，追加方法时必须 +1。
     *
     * 历史：
     *   1 —— getApiVersion / getIdentity / 回调注册注销
     *   2 —— searchPoi / planRoute / navigateTo / stopNavigation / getNavState
     *
     * 注意它是 `const val`，会被**内联**进使用处（`:service` 的 `getApiVersion()`）。
     * 因此升版本后必须重新编译服务端，否则返回值仍是旧值。
     */
    const val API_VERSION: Int = 2

    /**
     * 服务端登记的 action。
     *
     * 外部方**不要**用包名 bindService：applicationId 按车型覆盖为
     * com.sky.automap.<车型>，且 H6 / H9 可同时安装。正确姿势是
     * 用本 action 做 queryIntentServices，再用返回的组件名 bind，
     * 多个命中时用 [com.sky.automap.car.api.model.CarIdentity] 区分。
     */
    const val ACTION_SERVICE: String = "com.sky.automap.car.IRemoteCarService"

    /** 绑定权限（服务端以 signature|privileged 声明，见 service 模块的 manifest） */
    const val PERMISSION_BIND: String = "com.sky.automap.permission.BIND_CAR_SERVICE"

    // ------------------------------------------------------------------
    // 业务返回码（用于返回 int 的方法，如 navigateTo）
    //
    // 分段约定，方便调用方做粗粒度处理：
    //   0      成功
    //   1xx    参数错误（改调用方式能解决，重试无用）
    //   2xx    状态/环境问题（等就绪或换环境后可能解决）
    //   3xx    服务端内部错误（调用方无能为力，只需提示）
    // ------------------------------------------------------------------

    const val RESULT_OK: Int = 0

    /** 参数非法：空关键字、经纬度越界、destination 为 null */
    const val RESULT_INVALID_ARGUMENT: Int = 101

    /** 业务尚未就绪：还没收到 onServiceReady，调用方应等待而不是重试轰炸 */
    const val RESULT_NOT_READY: Int = 201

    /** 地图能力不可用：SDK 未注入或初始化失败，重试无意义，要提示用户 */
    const val RESULT_MAP_UNAVAILABLE: Int = 202

    /** 服务端内部异常，细节看服务端日志 */
    const val RESULT_INTERNAL_ERROR: Int = 300
}
