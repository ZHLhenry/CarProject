package com.baidu.mapapi

import android.content.Context
import android.util.Log
import com.baidu.mapapi.shim.BaiduSdk

/**
 * 百度 v21 影子 SDK 的初始化入口。
 *
 * 签名与 v22 一致，但 v21 **不要求**隐私协议先于初始化（v22 才强制），
 * 所以这里没有那道 `check`。这类「签名没变、语义变了」的改动只有靠
 * binding 覆写 + 静态校验里的语义清单来守。
 */
object SDKInitializer {

    private var ak: String = ""

    @Volatile
    private var privacyAgreed: Boolean = true

    @Volatile
    private var ready: Boolean = false

    fun setAgreePrivacy(agree: Boolean) {
        privacyAgreed = agree
    }

    fun initialize(context: Context, ak: String): Boolean {
        this.ak = ak
        ready = ak.isNotBlank()
        Log.i("BaiduShim", "Baidu Map ${BaiduSdk.mode} initialize -> ready=$ready, privacy=$privacyAgreed")
        return ready
    }

    fun isReady(): Boolean = ready
}
