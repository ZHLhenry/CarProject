package com.amap.api.services

import com.amap.api.shim.AmapSdk
import com.amap.api.shim.ApiRemoved
import com.amap.api.shim.ApiSince

/**
 * 高德检索 / 路线服务的并集影子层。
 *
 * 三个版本在这个包里改得最狠，正好演示继承链的价值：
 * - **检索**：850 是异步回调 `searchPOIAsyn`，910 换成同步 `search`；
 * - **POI 字段**：850 是 `uid/title/snippet`，910 改成 `poiId/name/address`；
 * - **路线**：850 回调单条，910 同步多策略。
 *
 * 这些「被改掉」的成员都带 [ApiRemoved]，它们只能出现在被新版 binding 覆写的方法里，
 * 否则运行期必然 `NoSuchMethodError`——静态校验任务检查的就是这一点。
 */

/** 坐标类型（与 `maps` 包下的 LatLng 是两个类型，真实 SDK 也是如此） */
data class LatLonPoint(val latitude: Double, val longitude: Double)

/**
 * POI 结构（三版字段的并集）。
 *
 * 850 访问 [uid] / [title] / [snippet]，910 访问 [poiId] / [name] / [address]，
 * 两组字段各自带版本守卫：用错了立即抛异常，而不是安静地拿到 null。
 */
class PoiItem internal constructor(
    private val legacyId: String,
    private val legacyTitle: String,
    private val legacySnippet: String,
    private val newId: String,
    private val newName: String,
    private val newAddress: String,
    val point: LatLonPoint,
) {

    @get:ApiRemoved(AmapSdk.V910)
    val uid: String
        get() {
            AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "PoiItem.uid")
            return legacyId
        }

    @get:ApiRemoved(AmapSdk.V910)
    val title: String
        get() {
            AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "PoiItem.title")
            return legacyTitle
        }

    @get:ApiRemoved(AmapSdk.V910)
    val snippet: String
        get() {
            AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "PoiItem.snippet")
            return legacySnippet
        }

    @get:ApiSince(AmapSdk.V910)
    val poiId: String
        get() {
            AmapSdk.gate(AmapSdk.V910, null, "PoiItem.poiId")
            return newId
        }

    @get:ApiSince(AmapSdk.V910)
    val name: String
        get() {
            AmapSdk.gate(AmapSdk.V910, null, "PoiItem.name")
            return newName
        }

    @get:ApiSince(AmapSdk.V910)
    val address: String
        get() {
            AmapSdk.gate(AmapSdk.V910, null, "PoiItem.address")
            return newAddress
        }
}

/** 910 起多一个 `hasMore`（分页），850 只有 `pois` */
data class PoiResult(val pois: List<PoiItem>, val hasMore: Boolean = false)

/**
 * POI 检索。
 *
 * - `PoiSearch(keyword)` + [searchPOIAsyn]：850；
 * - `PoiSearch()` + [search]：910。
 */
class PoiSearch {

    private var legacyKeyword: String = ""

    private var legacyPageSize: Int = 10

    /** 850：构造时就带关键词 */
    @ApiRemoved(AmapSdk.V910)
    constructor(keyword: String) {
        AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "PoiSearch(String)")
        legacyKeyword = keyword
    }

    /** 910：无参构造，关键词在 [search] 里给 */
    @ApiSince(AmapSdk.V910)
    constructor() {
        AmapSdk.gate(AmapSdk.V910, null, "PoiSearch()")
    }

    /** 850 新增：分页（910 换成 `search(..., pageSize)`，本方法被移除） */
    @ApiSince(AmapSdk.V850)
    @ApiRemoved(AmapSdk.V910)
    fun setPageSize(size: Int): PoiSearch = apply {
        AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "PoiSearch.setPageSize")
        legacyPageSize = size
    }

    /** 850：异步回调，错误码 1000 表示成功 */
    @ApiRemoved(AmapSdk.V910)
    fun searchPOIAsyn(around: LatLonPoint?, listener: (PoiResult?, Int) -> Unit) {
        AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "PoiSearch.searchPOIAsyn")
        val center = around ?: DEFAULT_CENTER
        listener(PoiResult(legacyResult(legacyKeyword, center).take(legacyPageSize)), SUCCESS_CODE)
    }

    /** 910：同步返回 + 分页 */
    @ApiSince(AmapSdk.V910)
    fun search(keyword: String, around: LatLonPoint?, pageSize: Int = 10): PoiResult {
        AmapSdk.gate(AmapSdk.V910, null, "PoiSearch.search")
        val center = around ?: DEFAULT_CENTER
        val all = legacyResult(keyword, center)
        return PoiResult(pois = all.take(pageSize), hasMore = all.size > pageSize)
    }

    private fun legacyResult(keyword: String, center: LatLonPoint): List<PoiItem> = listOf(
        poiItem("1", "$keyword·人民广场店", "黄浦区人民大道 1 号", center),
        poiItem("2", "$keyword·南京东路店", "黄浦区南京东路 300 号", LatLonPoint(center.latitude + 0.004, center.longitude + 0.003)),
        poiItem("3", "$keyword·陆家嘴店", "浦东新区世纪大道 8 号", LatLonPoint(center.latitude - 0.006, center.longitude + 0.008)),
        poiItem("4", "$keyword·静安寺店", "静安区南京西路 1686 号", LatLonPoint(center.latitude + 0.012, center.longitude - 0.009)),
    )

    private fun poiItem(index: String, name: String, address: String, point: LatLonPoint): PoiItem = PoiItem(
        legacyId = "amap${AmapSdk.mode}-$index",
        legacyTitle = name,
        legacySnippet = address,
        newId = "amap${AmapSdk.mode}-$index",
        newName = name,
        newAddress = address,
        point = point,
    )

    private companion object {
        const val SUCCESS_CODE = 1000

        val DEFAULT_CENTER = LatLonPoint(31.2304, 121.4737)
    }
}

/**
 * 路线。
 *
 * 850 只有 [points]；910 多一个 `strategy`（多策略时区分方案）。
 */
data class DrivingRoute(val points: List<LatLonPoint>, val strategy: Int = 0)

/** 路线规划：850 回调式单条，910 同步多策略 */
class RouteSearch {

    /** 850：回调式，`calculateDriveRoute` 在 910 已移除 */
    @ApiRemoved(AmapSdk.V910)
    fun calculateDriveRoute(
        start: LatLonPoint,
        end: LatLonPoint,
        listener: (DrivingRoute?, Int) -> Unit,
    ) {
        AmapSdk.gate(AmapSdk.V850, AmapSdk.V910, "RouteSearch.calculateDriveRoute")
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

    /** 910：同步返回多策略 */
    @ApiSince(AmapSdk.V910)
    fun calculateDriveRouteSync(
        start: LatLonPoint,
        end: LatLonPoint,
        strategies: List<Int>,
    ): List<DrivingRoute> {
        AmapSdk.gate(AmapSdk.V910, null, "RouteSearch.calculateDriveRouteSync")
        return strategies.mapIndexed { index, strategy ->
            val bend = 0.01 * (index + 1)
            DrivingRoute(
                strategy = strategy,
                points = listOf(
                    start,
                    LatLonPoint(
                        (start.latitude + end.latitude) / 2 + bend,
                        (start.longitude + end.longitude) / 2,
                    ),
                    end,
                ),
            )
        }
    }
}
