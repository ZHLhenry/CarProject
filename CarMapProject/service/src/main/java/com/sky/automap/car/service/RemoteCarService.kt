package com.sky.automap.car.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.sky.automap.car.api.CarApi
import com.sky.automap.car.api.IRemoteCarCallback
import com.sky.automap.car.api.IRemoteCarService
import com.sky.automap.car.api.model.CarIdentity
import com.sky.automap.car.api.model.CarLatLng
import com.sky.automap.car.api.model.CarNavState
import com.sky.automap.car.api.model.CarPoi
import com.sky.automap.map.api.IMapProvider
import com.sky.automap.map.factory.MapManager

/**
 * 对外 AIDL 契约的服务端实现。
 *
 * 分层位置：契约（`com.sky.automap:car-api`）→ **本类** → 地图能力层（`map-api`）。
 * 本类只做三件事：鉴权、DTO ↔ 领域模型转换、转发给能力层；
 * **不要**在这里写业务规则 —— 那些属于 `map-api` 的实现方。
 *
 * 运行位置：与 `:app` 同进程（不用 `android:process`）。
 * 原因见根 README 第十一节：`CarMapApplication.onCreate` 里有地图 SDK 初始化，
 * 独立进程会把它再跑一遍。
 *
 * 线程：所有 Stub 方法都在 Binder 线程池执行。这里**不能**碰 View / 主线程单例；
 * 需要主线程的重活应 post 到主线程，结果通过 oneway 回调送回。
 * 另注意 Binder 事务有 1MB 上限，列表类接口不能一次返回全量。
 */
class RemoteCarService : Service() {

    private val binder: IRemoteCarService.Stub = object : IRemoteCarService.Stub() {

        // ---------------- API 1 ----------------

        // 只读元信息，不额外校验调用方（高频且不改状态），靠 manifest 的
        // android:permission 兜住；见 enforceCaller() 的说明。
        override fun getApiVersion(): Int = CarApi.API_VERSION

        override fun getIdentity(): CarIdentity = RemoteCarBridge.identity(packageName)

        override fun registerCallback(callback: IRemoteCarCallback?) {
            enforceCaller()
            RemoteCarBridge.register(callback)
        }

        override fun unregisterCallback(callback: IRemoteCarCallback?) {
            enforceCaller()
            RemoteCarBridge.unregister(callback)
        }

        // ---------------- API 2 起追加 ----------------

        override fun searchPoi(keyword: String?, around: CarLatLng?): List<CarPoi> {
            enforceCaller()
            if (keyword.isNullOrBlank()) {
                Log.w(TAG, "searchPoi: 关键字为空，按参数错误返回空列表")
                return emptyList()
            }
            val provider = mapProvider() ?: return emptyList()
            return try {
                provider.searchPoi(keyword, around?.toLatLng())
                    .map { it.toCarPoi() }
                    .also { Log.i(TAG, "searchPoi($keyword) → ${it.size} 条") }
            } catch (e: Exception) {
                // 厂商 SDK 抛什么都必须在边界内兜住：未声明的运行时异常穿过 Binder
                // 只会变成调用方看不懂的 ParcelableException
                Log.e(TAG, "searchPoi 失败", e)
                emptyList()
            }
        }

        override fun planRoute(start: CarLatLng?, end: CarLatLng?): List<CarLatLng> {
            enforceCaller()
            if (start == null || end == null) {
                Log.w(TAG, "planRoute: 起终点为空")
                return emptyList()
            }
            val provider = mapProvider() ?: return emptyList()
            return try {
                provider.planRoute(start.toLatLng(), end.toLatLng())
                    .map { it.toCarLatLng() }
                    .also { Log.i(TAG, "planRoute → ${it.size} 个途经点") }
            } catch (e: Exception) {
                Log.e(TAG, "planRoute 失败", e)
                emptyList()
            }
        }

        override fun navigateTo(destination: CarPoi?): Int {
            enforceCaller()
            if (destination == null) return CarApi.RESULT_INVALID_ARGUMENT
            if (!RemoteCarBridge.isReady()) return CarApi.RESULT_NOT_READY
            if (mapProvider() == null) return CarApi.RESULT_MAP_UNAVAILABLE

            val state = NavStateHolder.start(destination)
            Log.i(TAG, "navigateTo(${destination.name}) → $state")
            // 推送失败不影响本次调用结果（NavStateHolder 已更新，客户端仍可 getNavState）
            RemoteCarBridge.broadcastNavState(state)
            return CarApi.RESULT_OK
        }

        /**
         * 不做就绪检查：停止导航在任何状态下都应该是安全的空操作，
         * 否则客户端崩溃重启后连「停掉上一次导航」都做不到。
         */
        override fun stopNavigation() {
            enforceCaller()
            val state = NavStateHolder.stop()
            Log.i(TAG, "stopNavigation → $state")
            RemoteCarBridge.broadcastNavState(state)
        }

        override fun getNavState(): CarNavState = NavStateHolder.current()
    }

    /**
     * 取地图能力层；未注入时返回 null 而不是抛异常 ——
     * 对外表现为「空列表 / RESULT_MAP_UNAVAILABLE」，调用方据此降级即可，
     * 不需要理解「SDK 没注入」这种内部状态。
     */
    private fun mapProvider(): IMapProvider? {
        if (!MapManager.isReady()) {
            Log.w(TAG, "地图实现尚未注入（MapManager.isReady()=false）")
            return null
        }
        return MapManager.provider()
    }

    @SuppressLint("BinderGetCallingInMainThread")
    override fun onBind(intent: Intent?): IBinder? {
        // 只承认契约 action：本模块的 service 若将来被别的 intent-filter 命中，
        // 返回 null 让 bind 明确失败，而不是把一个「猜错用途」的客户端接进来。
        val action = intent?.action
        if (action != null && action != CarApi.ACTION_SERVICE) {
            Log.w(TAG, "拒绝未知 action 的绑定：$action")
            return null
        }
        Log.i(TAG, "客户端已绑定 uid=${Binder.getCallingUid()}")
        return binder
    }

    /**
     * 纵深防御，只给「会改变状态或触发业务」的入口调用。
     *
     * manifest 上的 `android:permission` 已经保证「无权限的进程 bind 不上」，
     * 这里再查一次是为了：一旦有人为了联调临时删掉那条属性，Binder 层仍然拦得住。
     *
     * 只读入口（getApiVersion / getIdentity / getNavState）刻意不查：
     * checkCallingPermission 要走 PackageManager，而 getNavState 可能被外部轮询，
     * 每帧一次 IPC 不值得；这些方法泄露的也只是「身份与状态」这类非敏感信息。
     */
    private fun enforceCaller() {
        if (Binder.getCallingUid() == Process.myUid()) return
        val granted = checkCallingPermission(CarApi.PERMISSION_BIND) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw SecurityException(
                "调用方 uid=${Binder.getCallingUid()} 缺少 ${CarApi.PERMISSION_BIND}"
            )
        }
    }

    private companion object {
        const val TAG = "CarService"
    }
}
