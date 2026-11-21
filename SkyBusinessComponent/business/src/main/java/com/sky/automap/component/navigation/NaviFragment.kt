package com.sky.automap.component.navigation

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.sky.automap.component.R
import com.sky.automap.infrastructure.state.renderContent
import com.sky.automap.infrastructure.state.renderLoading
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.factory.MapManager
import com.sky.multistatelayout.widget.FrameStateLayout
import com.sky.mvvm.base.fragment.BaseVmFragment
import com.sky.mvvm.base.nav.popBack

/**
 * 导航业务组件页面（business AAR 的 navigation 包）：调用地图接口层完成路径规划并绘制到地图视图。
 */
class NaviFragment : BaseVmFragment<NaviViewModel>() {

    private lateinit var stateLayout: FrameStateLayout
    private lateinit var tvRoute: TextView
    private lateinit var mapContainer: FrameLayout
    private var mapView: IMapView? = null

    override fun layoutId(): Int = R.layout.fragment_navi

    override fun initView(savedInstanceState: Bundle?) {
        val root = requireView()
        stateLayout = root.findViewById(R.id.state_layout)
        tvRoute = root.findViewById(R.id.tv_navi_route)
        mapContainer = root.findViewById(R.id.map_container)

        root.findViewById<Button>(R.id.btn_plan_route).setOnClickListener { mViewModel.planRoute() }
        root.findViewById<Button>(R.id.btn_back_navi).setOnClickListener { popBack() }

        mapView = MapManager.createMapView(requireContext()).also {
            mapContainer.addView(it.view)
        }
    }

    override fun lazyLoadData() {
        mViewModel.planRoute()
    }

    override fun createObserver() {
        mViewModel.routeSummary.observe(viewLifecycleOwner) { summary ->
            stateLayout.renderContent()
            tvRoute.text = summary ?: "—"
        }
        mViewModel.waypoints.observe(viewLifecycleOwner) { points ->
            points?.forEachIndexed { index, latLng ->
                mapView?.addMarker(com.sky.automap.map.api.MapMarker("route-$index", latLng, "点 $index"))
            }
            points?.firstOrNull()?.let { mapView?.moveTo(it) }
        }
    }

    override fun showLoading(message: String) {
        stateLayout.renderLoading(message)
    }

    override fun dismissLoading() {
        stateLayout.renderContent()
    }
}
