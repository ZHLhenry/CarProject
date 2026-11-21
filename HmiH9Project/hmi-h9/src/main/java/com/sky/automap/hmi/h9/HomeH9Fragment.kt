package com.sky.automap.hmi.h9

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.sky.automap.component.R as BizR
import com.sky.mvvm.base.fragment.BaseVmFragment
import com.sky.mvvm.base.nav.navigateTo

/**
 * H9 HMI 首页：车型专属视觉与交互，导航图、主题色均与 H6 独立维护。
 */
class HomeH9Fragment : BaseVmFragment<HomeH9ViewModel>() {

    private lateinit var tvStatus: TextView

    override fun layoutId(): Int = R.layout.fragment_home_h9

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
        mViewModel.refreshStatus("基础框架 SkyMVVM / SkyWidget / SkyMultiStateLayout 已就绪")
    }

    override fun createObserver() {
        mViewModel.status.observe(viewLifecycleOwner) { tvStatus.text = it }
    }

    override fun showLoading(message: String) = Unit

    override fun dismissLoading() = Unit
}
