package com.sky.automap.component.poi

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sky.automap.map.api.MapPoi
import com.sky.automap.map.factory.MapManager
import com.sky.mvvm.base.viewmodel.BaseViewModel

/**
 * POI 业务组件 ViewModel。
 *
 * 只面向「地图门面 MapManager」编程：具体是高德还是百度，由壳工程注入的实现决定，
 * 本组件既不依赖任何厂商 SDK，也不直接读写地图注册表。
 */
class PoiViewModel : BaseViewModel() {

    private val _poiResult = MutableLiveData<List<MapPoi>>()
    val poiResult: LiveData<List<MapPoi>> = _poiResult

    private val _sdkLabel = MutableLiveData<String>()
    val sdkLabel: LiveData<String> = _sdkLabel

    fun search(keyword: String) {
        // 版本信息只用于展示；功能降级一律走能力位，不做版本号比较
        val caps = MapManager.capabilities()
        _sdkLabel.value = "${MapManager.sdkName()} · ${caps.sdkVersion} · ${caps.describe()}"
        _poiResult.value = MapManager.searchPoi(keyword)
    }
}
