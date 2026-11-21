package com.sky.automap.component.route

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.factory.MapManager
import com.sky.mvvm.base.viewmodel.BaseViewModel

/**
 * 路线（途经点）业务组件 ViewModel。
 *
 * 只依赖地图门面（MapManager），不感知具体厂商实现。
 */
class RouteViewModel : BaseViewModel() {

    private val _waypoints = MutableLiveData<List<LatLng>>(emptyList())
    val waypoints: LiveData<List<LatLng>> = _waypoints

    private val _summary = MutableLiveData<String>()
    val summary: LiveData<String> = _summary

    private var current: LatLng = START

    /** 追加一个途经点（每次沿地图实现给出的路径推进一段） */
    fun addWaypoint() {
        val route = MapManager.planRoute(current, END)
        val next = route.lastOrNull() ?: error(
            "路线规划没有返回任何点：(${current.latitude},${current.longitude}) → " +
                "(${END.latitude},${END.longitude})，地图实现=${MapManager.sdkName()}"
        )
        val list = _waypoints.value.orEmpty() + next
        _waypoints.value = list
        current = next
        _summary.value = "地图实现：${MapManager.sdkName()}　途经点：${list.size} 个"
    }

    fun clearWaypoints() {
        _waypoints.value = emptyList()
        current = START
        _summary.value = "已清空途经点"
    }

    private companion object {
        val START = LatLng(31.2304, 121.4737)
        val END = LatLng(31.2397, 121.4998)
    }
}
