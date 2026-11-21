package com.sky.automap.component.poi

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.sky.automap.component.R
import com.sky.automap.infrastructure.state.renderContent
import com.sky.automap.infrastructure.state.renderEmpty
import com.sky.automap.infrastructure.state.renderLoading
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapMarker
import com.sky.automap.map.factory.MapManager
import com.sky.multistatelayout.widget.FrameStateLayout
import com.sky.mvvm.base.fragment.BaseVmFragment
import com.sky.mvvm.base.nav.popBack

/**
 * POI 业务组件页面（business AAR 的 poi 包）。
 *
 * 职责：检索 POI 并在地图上打点，全程只依赖 Infrastructure + 地图门面（MapManager）。
 */
class PoiFragment : BaseVmFragment<PoiViewModel>() {

    private lateinit var stateLayout: FrameStateLayout
    private lateinit var tvResult: TextView
    private lateinit var mapContainer: FrameLayout
    private var mapView: IMapView? = null

    override fun layoutId(): Int = R.layout.fragment_poi

    override fun initView(savedInstanceState: Bundle?) {
        val root = requireView()
        stateLayout = root.findViewById(R.id.state_layout)
        tvResult = root.findViewById(R.id.tv_poi_result)
        mapContainer = root.findViewById(R.id.map_container)

        root.findViewById<Button>(R.id.btn_search_poi).setOnClickListener {
            mViewModel.search(SEARCH_KEYWORD)
        }
        root.findViewById<Button>(R.id.btn_back_poi).setOnClickListener { popBack() }

        attachMapView()
    }

    override fun lazyLoadData() {
        mViewModel.search(SEARCH_KEYWORD)
    }

    override fun createObserver() {
        mViewModel.poiResult.observe(viewLifecycleOwner) { pois ->
            if (pois.isNullOrEmpty()) {
                stateLayout.renderEmpty("附近没有搜索到 $SEARCH_KEYWORD")
                return@observe
            }
            stateLayout.renderContent()
            tvResult.text = buildString {
                append("地图实现：")
                append(mViewModel.sdkLabel.value ?: "-")
                append("\n")
                pois.forEach { poi ->
                    append("· ${poi.name}  |  ${poi.address}\n")
                }
            }
            pois.forEach { poi ->
                mapView?.addMarker(MapMarker(poi.id, poi.position, poi.name))
            }
            mapView?.moveTo(pois.first().position)
        }
    }

    override fun showLoading(message: String) {
        stateLayout.renderLoading(message)
    }

    override fun dismissLoading() {
        stateLayout.renderContent()
    }

    private fun attachMapView() {
        val view = MapManager.createMapView(requireContext())
        mapView = view
        mapContainer.addView(view.view)
        view.moveTo(LatLng(31.2304, 121.4737))
    }

    private companion object {
        const val SEARCH_KEYWORD = "充电站"
    }
}
