package com.sky.automap.component.route

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.sky.automap.component.R
import com.sky.automap.infrastructure.state.renderContent
import com.sky.automap.infrastructure.state.renderLoading
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.api.MapMarker
import com.sky.automap.map.factory.MapManager
import com.sky.multistatelayout.widget.FrameStateLayout
import com.sky.mvvm.base.fragment.BaseVmFragment
import com.sky.mvvm.base.nav.popBack

/**
 * 路线业务组件页面（business AAR 的 route 包）：途经点增删与地图打点。
 */
class RouteFragment : BaseVmFragment<RouteViewModel>() {

    private lateinit var stateLayout: FrameStateLayout
    private lateinit var tvSummary: TextView
    private lateinit var mapContainer: FrameLayout
    private var mapView: IMapView? = null

    override fun layoutId(): Int = R.layout.fragment_route

    override fun initView(savedInstanceState: Bundle?) {
        val root = requireView()
        stateLayout = root.findViewById(R.id.state_layout)
        tvSummary = root.findViewById(R.id.tv_route_summary)
        mapContainer = root.findViewById(R.id.map_container)

        root.findViewById<Button>(R.id.btn_add_waypoint).setOnClickListener { mViewModel.addWaypoint() }
        root.findViewById<Button>(R.id.btn_clear_waypoint).setOnClickListener {
            mViewModel.clearWaypoints()
            mapView?.clear()
        }
        root.findViewById<Button>(R.id.btn_back_route).setOnClickListener { popBack() }

        mapView = MapManager.createMapView(requireContext()).also {
            mapContainer.addView(it.view)
        }
    }

    override fun lazyLoadData() {
        mViewModel.addWaypoint()
    }

    override fun createObserver() {
        mViewModel.summary.observe(viewLifecycleOwner) { text ->
            stateLayout.renderContent()
            tvSummary.text = text ?: "—"
        }
        mViewModel.waypoints.observe(viewLifecycleOwner) { points ->
            points?.forEachIndexed { index, latLng ->
                mapView?.addMarker(MapMarker("waypoint-$index", latLng, "途经点 ${index + 1}"))
            }
        }
    }

    override fun showLoading(message: String) {
        stateLayout.renderLoading(message)
    }

    override fun dismissLoading() {
        stateLayout.renderContent()
    }
}
