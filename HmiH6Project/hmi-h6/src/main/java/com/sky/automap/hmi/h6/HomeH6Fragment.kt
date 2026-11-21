package com.sky.automap.hmi.h6

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.sky.automap.component.R as BizR
import com.sky.mvvm.base.fragment.BaseVmFragment
import com.sky.mvvm.base.nav.navigateTo

/**
 * H6 HMI 首页：作为导航图起点，向业务组件层（business.aar 里的 poi / navigation / route 三张子图）发起跳转。
 */
class HomeH6Fragment : BaseVmFragment<HomeH6ViewModel>() {

    private lateinit var tvStatus: TextView

    override fun layoutId(): Int = R.layout.fragment_home_h6

    override fun initView(savedInstanceState: Bundle?) {
        val root = requireView()
        tvStatus = root.findViewById(R.id.tv_status)

        root.findViewById<Button>(R.id.btn_open_poi).setOnClickListener {
            navigateTo(BizR.id.poi_graph)
        }
        root.findViewById<Button>(R.id.btn_open_navi).setOnClickListener {
            navigateTo(BizR.id.navi_graph)
        }
        root.findViewById<Button>(R.id.btn_open_route).setOnClickListener {
            navigateTo(BizR.id.route_graph)
        }
    }

    override fun lazyLoadData() {
        // 文案走字符串资源（values / values-en / values-zh-rTW），不要在代码里写中文
        mViewModel.refreshStatus(getString(R.string.h6_home_status_ready))
    }

    override fun createObserver() {
        mViewModel.status.observe(viewLifecycleOwner) { tvStatus.text = it }
    }

    override fun showLoading(message: String) = Unit

    override fun dismissLoading() = Unit
}
