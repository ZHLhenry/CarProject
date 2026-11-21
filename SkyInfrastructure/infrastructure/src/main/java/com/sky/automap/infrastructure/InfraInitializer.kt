package com.sky.automap.infrastructure

import android.app.Application
import com.sky.mvvm.SkyMVVMLib
import com.sky.mvvm.SkyMVVMLibConfig
import com.sky.multistatelayout.SkyMultiStateLayout

/**
 * 基建层统一初始化入口。
 *
 * 壳工程 `Application.onCreate()` 中只需调用一次 [init]，即可完成：
 * 1. SkyMVVM 初始化（网络层 / SkyFlow 事件总线 / 页面基类的前置校验）；
 * 2. SkyMultiStateLayout 全局状态视图注册（加载中 / 空数据 / 错误 / 断网）。
 *
 * 业务模块无需各自初始化，避免重复调用导致状态错乱。
 */
object InfraInitializer {

    @Volatile
    private var initialized = false

    fun init(application: Application) {
        if (initialized) return
        initialized = true

        initMvvm(application)
        initMultiStateLayout()
    }

    private fun initMvvm(application: Application) {
        if (SkyMVVMLib.isInitialized) return
        SkyMVVMLib.init(
            SkyMVVMLibConfig.Builder(application)
                // SkyFlow 事件总线（跨模块事件分发）
                .enableSkyFlow(enableSkyFlowLib = true)
                .build()
        )
    }

    private fun initMultiStateLayout() {
        SkyMultiStateLayout.init()
            .setLoadingView(
                R.layout.infra_state_loading,
                R.id.tv_state_hint,
                applicationHints[STATE_LOADING]
            )
            .setEmptyView(
                R.layout.infra_state_empty,
                R.id.tv_state_hint,
                applicationHints[STATE_EMPTY],
                R.id.tv_state_action
            )
            .setErrorView(
                R.layout.infra_state_error,
                R.id.tv_state_hint,
                applicationHints[STATE_ERROR],
                R.id.tv_state_action
            )
            .setNoNetworkView(
                R.layout.infra_state_no_network,
                R.id.tv_state_hint,
                applicationHints[STATE_NO_NETWORK],
                R.id.tv_state_action
            )
    }

    private const val STATE_LOADING = 0
    private const val STATE_EMPTY = 1
    private const val STATE_ERROR = 2
    private const val STATE_NO_NETWORK = 3

    /** 全局状态视图默认提示文案 */
    private val applicationHints = mapOf(
        STATE_LOADING to "加载中…",
        STATE_EMPTY to "暂无数据",
        STATE_ERROR to "加载失败，请稍后重试",
        STATE_NO_NETWORK to "网络不可用，请检查网络设置",
    )
}
