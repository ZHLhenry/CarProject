package com.baidu.mapapi.search

import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.shim.ApiRemoved
import com.baidu.mapapi.shim.ApiSince
import com.baidu.mapapi.shim.BaiduSdk

/**
 * 百度检索 / 路线服务的并集影子层。
 *
 * v22 相对 v21 的破坏性改动都在这个包里：
 * - 检索：`searchInCity`（异步）→ `search`（同步 + 分页）；
 * - POI 字段：`uid/addr` → `poiId/address`；
 * - 路线：`drivingSearch`（回调单条）→ `drivingSearchSync`（同步多策略）。
 */

/**
 * POI 结构（两版字段的并集）。
 *
 * v21 读 [uid] / [addr]，v22 读 [poiId] / [address]，用错版本立即抛异常。
 */
class PoiInfo internal constructor(
    private val legacyUid: String,
    private val legacyAddr: String,
    private val newId: String,
    private val newAddress: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {

    @get:ApiRemoved(BaiduSdk.V22)
    val uid: String
        get() {
            BaiduSdk.gate(BaiduSdk.V21, BaiduSdk.V22, "PoiInfo.uid")
            return legacyUid
        }

    @get:ApiRemoved(BaiduSdk.V22)
    val addr: String
        get() {
            BaiduSdk.gate(BaiduSdk.V21, BaiduSdk.V22, "PoiInfo.addr")
            return legacyAddr
        }

    @get:ApiSince(BaiduSdk.V22)
    val poiId: String
        get() {
            BaiduSdk.gate(BaiduSdk.V22, null, "PoiInfo.poiId")
            return newId
        }

    @get:ApiSince(BaiduSdk.V22)
    val address: String
        get() {
            BaiduSdk.gate(BaiduSdk.V22, null, "PoiInfo.address")
            return newAddress
        }
}

/** v22 的同步检索结果 */
@ApiSince(BaiduSdk.V22)
data class PoiSearchResult(val pois: List<PoiInfo>, val hasMore: Boolean = false)

/** POI 检索：v21 异步 + 强制城市；v22 同步 + 分页 */
class PoiSearch {

    /** v21：异步回调，错误码 0 表示成功；`searchInCity` 在 v22 已移除 */
    @ApiRemoved(BaiduSdk.V22)
    fun searchInCity(
        city: String,
        keyword: String,
        listener: (List<PoiInfo>?, Int) -> Unit,
    ) {
        BaiduSdk.gate(BaiduSdk.V21, BaiduSdk.V22, "PoiSearch.searchInCity")
        listener(all(keyword), SUCCESS_CODE)
    }

    /** v22：同步返回 + 分页 */
    @ApiSince(BaiduSdk.V22)
    fun search(keyword: String, city: String, pageSize: Int = 10): PoiSearchResult {
        BaiduSdk.gate(BaiduSdk.V22, null, "PoiSearch.search")
        val all = all(keyword)
        return PoiSearchResult(pois = all.take(pageSize), hasMore = all.size > pageSize)
    }

    private fun all(keyword: String): List<PoiInfo> = listOf(
        poiInfo("1", "$keyword·外滩店", "黄浦区中山东一路 1 号", 31.2400, 121.4900),
        poiInfo("2", "$keyword·徐家汇店", "徐汇区漕溪北路 100 号", 31.1900, 121.4370),
        poiInfo("3", "$keyword·五角场店", "杨浦区淞沪路 77 号", 31.3000, 121.5100),
    )

    private fun poiInfo(index: String, name: String, address: String, latitude: Double, longitude: Double): PoiInfo =
        PoiInfo(
            legacyUid = "bd${BaiduSdk.mode}-$index",
            legacyAddr = address,
            newId = "bd${BaiduSdk.mode}-$index",
            newAddress = address,
            name = name,
            latitude = latitude,
            longitude = longitude,
        )

    private companion object {
        const val SUCCESS_CODE = 0
    }
}

/** 路线规划：v21 回调单条；v22 同步多策略 */
class RoutePlanSearch {

    /** v21：回调式，`drivingSearch` 在 v22 已移除 */
    @ApiRemoved(BaiduSdk.V22)
    fun drivingSearch(
        start: LatLng,
        end: LatLng,
        listener: (List<LatLng>?, Int) -> Unit,
    ) {
        BaiduSdk.gate(BaiduSdk.V21, BaiduSdk.V22, "RoutePlanSearch.drivingSearch")
        listener(
            listOf(
                start,
                LatLng((start.latitude + end.latitude) / 2, (start.longitude + end.longitude) / 2),
                end,
            ),
            SUCCESS_CODE,
        )
    }

    /** v22：同步返回多策略 */
    @ApiSince(BaiduSdk.V22)
    fun drivingSearchSync(
        start: LatLng,
        end: LatLng,
        strategies: List<Int>,
    ): List<List<LatLng>> {
        BaiduSdk.gate(BaiduSdk.V22, null, "RoutePlanSearch.drivingSearchSync")
        return strategies.mapIndexed { index, _ ->
            val bend = 0.008 * (index + 1)
            listOf(
                start,
                LatLng((start.latitude + end.latitude) / 2 + bend, (start.longitude + end.longitude) / 2),
                end,
            )
        }
    }

    private companion object {
        const val SUCCESS_CODE = 0
    }
}
