package com.sky.automap.component.navigation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.factory.MapManager
import com.sky.mvvm.base.viewmodel.BaseViewModel

/**
 * 导航业务组件 ViewModel：路径规划。
 *
 * 只依赖地图门面（MapManager），不感知具体厂商实现。
 */
class NaviViewModel : BaseViewModel() {

    private val _waypoints = MutableLiveData<List<LatLng>>()
    val waypoints: LiveData<List<LatLng>> = _waypoints

    private val _routeSummary = MutableLiveData<String>()
    val routeSummary: LiveData<String> = _routeSummary

    fun planRoute() {
        val route = MapManager.planRoute(START, END)
        _waypoints.value = route
        _routeSummary.value = buildString {
            append("地图实现：").append(MapManager.sdkName())
            append("\n起点：").append(START.latitude).append(", ").append(START.longitude)
            append("\n终点：").append(END.latitude).append(", ").append(END.longitude)
            append("\n途经点数量：").append(route.size)
        }
    }

    private companion object {
        val START = LatLng(31.2304, 121.4737)
        val END = LatLng(31.2397, 121.4998)
    }
}
