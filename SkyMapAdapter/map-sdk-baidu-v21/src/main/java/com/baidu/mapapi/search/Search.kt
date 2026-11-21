package com.baidu.mapapi.search

import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.shim.BaiduSdk

/**
 * 百度 v21 影子 SDK：检索 / 路线。
 *
 * - POI 字段是 `uid / addr`；
 * - 检索只有异步 [PoiSearch.searchInCity]（且强制传城市）；
 * - 路线只有回调式单条 [RoutePlanSearch.drivingSearch]。
 */
class PoiInfo internal constructor(
    val uid: String,
    val addr: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/** POI 检索：v21 异步 + 强制城市 */
class PoiSearch {

    fun searchInCity(
        city: String,
        keyword: String,
        listener: (List<PoiInfo>?, Int) -> Unit,
    ) {
        listener(all(keyword), SUCCESS_CODE)
    }

    private fun all(keyword: String): List<PoiInfo> = listOf(
        poiInfo("1", "$keyword·外滩店", "黄浦区中山东一路 1 号", 31.2400, 121.4900),
        poiInfo("2", "$keyword·徐家汇店", "徐汇区漕溪北路 100 号", 31.1900, 121.4370),
        poiInfo("3", "$keyword·五角场店", "杨浦区淞沪路 77 号", 31.3000, 121.5100),
    )

    private fun poiInfo(index: String, name: String, address: String, latitude: Double, longitude: Double): PoiInfo =
        PoiInfo(
            uid = "bd${BaiduSdk.mode}-$index",
            addr = address,
            name = name,
            latitude = latitude,
            longitude = longitude,
        )

    private companion object {
        const val SUCCESS_CODE = 0
    }
}

/** 路线规划：v21 回调单条 */
class RoutePlanSearch {

    fun drivingSearch(
        start: LatLng,
        end: LatLng,
        listener: (List<LatLng>?, Int) -> Unit,
    ) {
        listener(
            listOf(
                start,
                LatLng((start.latitude + end.latitude) / 2, (start.longitude + end.longitude) / 2),
                end,
            ),
            SUCCESS_CODE,
        )
    }

    private companion object {
        const val SUCCESS_CODE = 0
    }
}
