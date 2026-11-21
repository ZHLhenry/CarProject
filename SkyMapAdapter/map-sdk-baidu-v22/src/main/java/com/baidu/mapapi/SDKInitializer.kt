package com.baidu.mapapi

import android.content.Context
import android.util.Log
import com.baidu.mapapi.shim.ApiSince
import com.baidu.mapapi.shim.BaiduSdk

/**
 * 百度 SDK 初始化入口（v21 / v22 并集）。
 *
 * 签名两版一致，但 v22 多了**语义要求**：`setAgreePrivacy(true)` 必须早于 `initialize`。
 * 这类改动 API 面 diff 抓不到，只能靠 binding 覆写 + 静态校验里的「语义变更清单」。
 */
object SDKInitializer {

    private var ak: String = ""

    @Volatile
    private var privacyAgreed: Boolean = true

    @Volatile
    private var ready: Boolean = false

    @ApiSince(BaiduSdk.V21)
    fun setAgreePrivacy(agree: Boolean) {
        privacyAgreed = agree
    }

    @ApiSince(BaiduSdk.V21)
    fun initialize(context: Context, ak: String): Boolean {
        if (BaiduSdk.currentLevel >= BaiduSdk.levelOf(BaiduSdk.V22)) {
            check(privacyAgreed) {
                "[Baidu v22] 未同意隐私协议，必须先调用 SDKInitializer.setAgreePrivacy(true)"
            }
        }
        this.ak = ak
        ready = ak.isNotBlank()
        Log.i("BaiduShim", "Baidu Map ${BaiduSdk.mode} initialize -> ready=$ready, privacy=$privacyAgreed")
        return ready
    }

    fun isReady(): Boolean = ready
}
