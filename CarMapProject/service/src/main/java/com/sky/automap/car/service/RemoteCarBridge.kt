package com.sky.automap.car.service

import android.os.RemoteCallbackList
import android.util.Log
import com.sky.automap.car.api.IRemoteCarCallback
import com.sky.automap.car.api.model.CarIdentity
import com.sky.automap.car.api.model.CarNavState

/**
 * service 与壳工程之间的桥：持有运行期身份、登记客户端回调。
 *
 * 为什么是 object 而不是 Service 的成员：
 * - [RemoteCallbackList] 的生命周期应与进程一致，Service 被系统重建时不应丢掉
 *   已注册的客户端（否则外部方要重新注册，而它并不知道需要重注册）；
 * - 注入动作发生在 `Application.onCreate`，早于 `Service.onCreate`，
 *   需要一个比 Service 更早存在的容器接住它。
 *
 * 线程约定：所有方法都可能被 Binder 线程调用，故内部只用 `@Volatile` 与
 * `RemoteCallbackList`（自身线程安全），不额外加锁 —— 加锁反而容易在
 * broadcast 期间与 register/unregister 交叉而死锁。
 */
object RemoteCarBridge {

    private const val TAG = "CarService"

    @Volatile
    private var env: CarServiceEnv? = null

    private val callbacks = RemoteCallbackList<IRemoteCarCallback>()

    /**
     * 壳工程注入运行期身份。重复调用以最后一次为准；
     * 内容没变时不再广播，避免客户端把「重复的 onServiceReady」误读成业务重启。
     */
    fun attach(env: CarServiceEnv) {
        val changed = this.env != env
        this.env = env
        if (!changed) {
            Log.d(TAG, "attach 内容未变，跳过就绪广播")
            return
        }
        Log.i(TAG, "attach ${env.applicationId} car=${env.carModel} channel=${env.channelId}")
        broadcast("onServiceReady") { it.onServiceReady() }
    }

    /** 业务是否已就绪；`bindService` 成功**不**代表它就绪 */
    fun isReady(): Boolean = env != null

    /**
     * 注册回调。若此刻已就绪，立即补发一次 [IRemoteCarCallback.onServiceReady]——
     * 外部方通常「先 bind 再注册」，不能指望它一定赶得上 attach 的那次广播。
     */
    fun register(callback: IRemoteCarCallback?) {
        if (callback == null) return
        // register() 返回 false 表示同一个 binder 已注册过（RemoteCallbackList 按 IBinder 去重）。
        // 这种情况不再补发 onServiceReady，否则客户端每调一次注册就多收一次「就绪」。
        val firstTime = callbacks.register(callback)
        if (!firstTime) {
            Log.d(TAG, "回调重复注册，已去重")
            return
        }
        if (isReady()) {
            try {
                callback.onServiceReady()
            } catch (e: Exception) {
                Log.w(TAG, "补发 onServiceReady 失败", e)
            }
        }
    }

    fun unregister(callback: IRemoteCarCallback?) {
        if (callback == null) return
        callbacks.unregister(callback)
    }

    /**
     * 返回给客户端的身份。
     *
     * 未注入时用包名兜底而不是返回 null：契约里它不是可空返回值，
     * 返回 null 会让调用方在 `CarIdentity` 上直接 NPE，比返回 unknown 更难排查。
     */
    fun identity(packageName: String): CarIdentity {
        val current = env
        if (current == null) {
            Log.w(TAG, "identity() 在 attach 之前被调用，返回 unknown（packageName=$packageName）")
            return CarIdentity(packageName, "unknown", "unknown", "unknown")
        }
        return CarIdentity(
            applicationId = current.applicationId,
            carModel = current.carModel,
            channelId = current.channelId,
            mapVendor = current.mapVendor,
        )
    }

    /** 推送导航状态变化（仅状态**真正变化**时调用，不要周期性刷） */
    fun broadcastNavState(state: CarNavState) {
        broadcast("onNavStateChanged") { it.onNavStateChanged(state) }
    }

    /**
     * 统一广播出口。
     *
     * 三条硬约束都集中在这里，避免每个事件各写一遍：
     * 1. begin/finish 必须成对，否则 RemoteCallbackList 一直处于 broadcast 状态，
     *    后续 register/unregister 会抛 IllegalStateException；
     * 2. broadcast 期间**不能** register/unregister，所以单条回调失败只能记日志 ——
     *    死连接由 RemoteCallbackList 自己的 binderDied 机制回收；
     * 3. 回调是 oneway 的，客户端死亡只会抛异常，不会阻塞，逐条兜住即可。
     */
    private fun broadcast(event: String, action: (IRemoteCarCallback) -> Unit) {
        val count = callbacks.beginBroadcast()
        try {
            for (index in 0 until count) {
                try {
                    action(callbacks.getBroadcastItem(index))
                } catch (e: Exception) {
                    Log.w(TAG, "$event 回调失败，等待 binderDied 回收", e)
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }
}
