package com.sky.automap.infrastructure.state

import android.view.ViewGroup
import com.sky.automap.infrastructure.R
import com.sky.multistatelayout.widget.FrameStateLayout

/**
 * 多状态布局统一渲染扩展。
 *
 * SkyMultiStateLayout 的「无参便捷重载」定义在库内部接口中，跨模块不可见，
 * 因此基建层在此统一封装一层语义化 API：业务模块只写 `renderLoading()` / `renderContent()`，
 * 状态布局的资源与文案由基建层集中维护，避免各业务模块重复堆积状态视图。
 */

private fun matchParentParams() =
    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

/** 内容视图 */
fun FrameStateLayout.renderContent() = showContent()

/** 加载中 */
fun FrameStateLayout.renderLoading(hint: String = "加载中…") =
    showLoading(R.layout.infra_state_loading, matchParentParams(), R.id.tv_state_hint, hint)

/** 空数据 */
fun FrameStateLayout.renderEmpty(hint: String = "暂无数据") =
    showEmpty(R.layout.infra_state_empty, matchParentParams(), R.id.tv_state_hint, hint, R.id.tv_state_action)

/** 错误 */
fun FrameStateLayout.renderError(hint: String = "加载失败，请稍后重试") =
    showError(R.layout.infra_state_error, matchParentParams(), R.id.tv_state_hint, hint, R.id.tv_state_action)

/** 断网 */
fun FrameStateLayout.renderNoNetwork(hint: String = "网络不可用，请检查网络设置") =
    showNoNetwork(
        R.layout.infra_state_no_network,
        matchParentParams(),
        R.id.tv_state_hint,
        hint,
        R.id.tv_state_action
    )
