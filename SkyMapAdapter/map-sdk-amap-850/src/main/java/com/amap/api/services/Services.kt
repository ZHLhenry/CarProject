package com.amap.api.services

import com.amap.api.shim.AmapSdk

/**
 * 高德 850 影子 SDK：检索 / 路线（对应真实 aar 的 `com.amap.api.services`）。
 *
 * 相对更早版本的增量只有一处：检索支持分页 [PoiSearch.setPageSize]。
 * 检索仍走异步回调，POI 字段仍是 `uid / title / snippet`；
 * 910 才改成同步返回 + `poiId / name / address`。
 */
data class LatLonPoint(val latitude: Double, val longitude: Double)

class PoiItem internal constructor(
    val uid: String,
    val title: String,
    val snippet: String,
    val point: LatLonPoint,
)

data class PoiResult(val pois: List<PoiItem>)

/** POI 检索：基础检索 + 850 的分页 */
class PoiSearch(private val keyword: String) {

    private var pageSize: Int = DEFAULT_PAGE_SIZE

    /** 850 新增：分页 */
    fun setPageSize(size: Int): PoiSearch = apply { pageSize = size }

    fun searchPOIAsyn(around: LatLonPoint?, listener: (PoiResult?, Int) -> Unit) {
        val center = around ?: DEFAULT_CENTER
        listener(PoiResult(legacyResult(keyword, center).take(pageSize)), SUCCESS_CODE)
    }

    private fun legacyResult(keyword: String, center: LatLonPoint): List<PoiItem> = listOf(
        poiItem("1", "$keyword·人民广场店", "黄浦区人民大道 1 号", center),
        poiItem("2", "$keyword·南京东路店", "黄浦区南京东路 300 号", LatLonPoint(center.latitude + 0.004, center.longitude + 0.003)),
        poiItem("3", "$keyword·陆家嘴店", "浦东新区世纪大道 8 号", LatLonPoint(center.latitude - 0.006, center.longitude + 0.008)),
        poiItem("4", "$keyword·静安寺店", "静安区南京西路 1686 号", LatLonPoint(center.latitude + 0.012, center.longitude - 0.009)),
    )

    private fun poiItem(index: String, name: String, address: String, point: LatLonPoint): PoiItem =
        PoiItem(
            uid = "amap${AmapSdk.mode}-$index",
            title = name,
            snippet = address,
            point = point,
        )

    private companion object {
        const val SUCCESS_CODE = 1000

        const val DEFAULT_PAGE_SIZE = 10

        val DEFAULT_CENTER = LatLonPoint(31.2304, 121.4737)
    }
}

data class DrivingRoute(val points: List<LatLonPoint>)

/** 路线规划：850 没有变化，仍是回调式单条 */
class RouteSearch {

    fun calculateDriveRoute(
        start: LatLonPoint,
        end: LatLonPoint,
        listener: (DrivingRoute?, Int) -> Unit,
    ) {
        listener(
            DrivingRoute(
                points = listOf(
                    start,
                    LatLonPoint((start.latitude + end.latitude) / 2, (start.longitude + end.longitude) / 2),
                    end,
                ),
            ),
            1000,
        )
    }
}
