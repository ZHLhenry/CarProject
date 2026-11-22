package com.sky.automap.map.factory

import android.content.Context
import com.sky.automap.map.api.IMapProvider
import com.sky.automap.map.api.IMapView
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapCapabilities
import com.sky.automap.map.api.MapPoi
import com.sky.automap.map.api.MapSdkType

/**
 * 地图能力门面（map-api.aar 的 `map.factory` 包）：对上层提供统一的「取用入口」。
 *
 * 分层约定（同一个 AAR 内按包分）：
 * - `com.sky.automap.map.api`：只有接口与数据模型，**不含任何可变状态**；
 * - `com.sky.automap.map.factory`：注册表（`internal`）+ 本门面，负责「注入、取用、兜底」；
 * - 业务组件：只依赖 map-api，不直接读写注册表；
 * - 壳工程：打包时 [install] 具体实现（H6 → 高德，H9 → 百度）。
 *
 * 为什么不单独拆一个 map-factory 模块：门面只有两个文件、十几行构建脚本，而业务组件与
 * 壳工程本来就要依赖 map-api 拿数据模型，拆开只多一个坐标、一次发布、一行版本表。
 * 可变状态只许出现在本包，`map.api` 包保持「零可变状态」的纯契约。
 *
 * 未来要加「多实例池 / SDK 初始化失败降级 / 切换耗时埋点」等策略，都在本类内部完成，
 * `map.api` 包内的二进制契约保持不变。
 */
object MapManager {

    /** 注入具体地图实现（仅壳工程调用） */
    fun install(mapProvider: IMapProvider) = MapProviderRegistry.install(mapProvider)

    /** 当前是否已完成注入 */
    fun isReady(): Boolean = MapProviderRegistry.isInstalled()

    /** 当前实现（需要厂商特有高级能力时的逃生舱口） */
    fun provider(): IMapProvider = MapProviderRegistry.current()

    /** 当前实现的 SDK 类型 */
    fun sdkType(): MapSdkType = provider().sdkType

    /** 当前地图 SDK 名称（用于调试面板 / 关于页展示） */
    fun sdkName(): String = if (isReady()) sdkType().label else "未接入"

    /** 当前绑定的 SDK 版本号，如 "850" / "910" / "v22" */
    fun sdkVersion(): String = provider().sdkVersion

    /**
     * 当前实现的能力集——业务层做功能降级时只看这里，**不要比较 [sdkVersion]**。
     *
     * 例：`if (MapManager.capabilities().supportsOfflineMap) showOfflineEntry()`
     */
    fun capabilities(): MapCapabilities =
        if (isReady()) provider().capabilities else MapCapabilities.NONE

    /** 形如 `amap-910`，用于日志与埋点 */
    fun mapTag(): String = capabilities().tag

    /** 创建地图视图 */
    fun createMapView(context: Context): IMapView = provider().createMapView(context)

    /** POI 搜索（关键字 + 可选中心点） */
    fun searchPoi(keyword: String, around: LatLng? = null): List<MapPoi> =
        provider().searchPoi(keyword, around)

    /** 路径规划，返回途经点序列 */
    fun planRoute(start: LatLng, end: LatLng): List<LatLng> =
        provider().planRoute(start, end)

    /**
     * 多路线规划（结果条数取决于 `capabilities().supportsMultiRoute`）。
     */
    fun planRoutes(start: LatLng, end: LatLng): List<List<LatLng>> =
        provider().planRoutes(start, end)
}
