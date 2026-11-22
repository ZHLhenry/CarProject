// ============================================================================
// 对外 AIDL 契约：车机应用（CarMap）向外部应用暴露的能力入口
//
// 兼容规则（一旦发布，本接口即对第三方生效）：
//   1. 方法只能**追加在末尾** —— AIDL 按声明顺序分配 transaction code，
//      插在中间会让旧客户端调错方法（表现为参数错位，极难排查）；
//   2. 不改已有方法的参数类型/顺序、不删方法；
//   3. 业务失败用返回值表达，不要靠抛异常 —— 跨进程只保证 RemoteException 语义；
//   4. 外部调用方调用新方法前必须先看 getApiVersion()，不要盲调。
//
// 版本历史（接口能力版本 = CarApi.API_VERSION，与 Maven 坐标无关）：
//   API 1 —— getApiVersion / getIdentity / 回调注册注销
//   API 2 —— 追加 searchPoi / planRoute / navigateTo / stopNavigation / getNavState
// 当前 AAR 坐标 1.0.0 内已含 API 2；**不要**用坐标判断能力，只看 getApiVersion()。
// ============================================================================
package com.sky.automap.car.api;

import com.sky.automap.car.api.IRemoteCarCallback;
import com.sky.automap.car.api.model.CarIdentity;
import com.sky.automap.car.api.model.CarLatLng;
import com.sky.automap.car.api.model.CarNavState;
import com.sky.automap.car.api.model.CarPoi;

interface IRemoteCarService {

    // ---------------- API 1 ----------------

    /**
     * 协议版本：每追加一次方法（或新增语义）就 +1，调用方以此判断能力集。
     *
     * 它是本接口里唯一「不自述就没法演进」的方法：旧服务端不认识新方法的
     * transaction code，只会回一句无指向性的 Transaction failed。
     */
    int getApiVersion();

    /**
     * 服务端自述。
     *
     * 为什么必须有它：applicationId 按车型覆盖为 com.sky.automap.<车型>，
     * 且 H6 / H9 两个包可以同时安装 —— 调用方按 action 查询可能命中多个实现，
     * 只能靠本方法的返回值区分该连哪个。
     */
    CarIdentity getIdentity();

    /** 注册状态回调；同一回调重复注册由服务端去重（基于 RemoteCallbackList） */
    void registerCallback(IRemoteCarCallback callback);

    /** 注销回调；不注销会导致服务端一直持有 binder 引用（内存泄漏 + 死连接） */
    void unregisterCallback(IRemoteCarCallback callback);

    // ---------------- API 2 起追加（只能继续往末尾加） ----------------

    /**
     * [API 2] POI 关键字搜索。
     *
     * @param keyword 关键字，空串视为参数错误（返回空列表）
     * @param around  可选中心点；传 null 表示按服务端默认位置
     * @return 结果列表。**失败与「没搜到」都返回空列表**，不用异常表达业务结果；
     *         列表长度由服务端限流，外部不要假设能一次拿全。
     *
     * 注意：本调用是**同步**的（Binder 事务会等真实搜索返回）。外部方不要在
     * UI 线程直接调；单次结果还受 1MB 事务上限约束，需要大量结果时要分页。
     */
    List<CarPoi> searchPoi(String keyword, in CarLatLng around);

    /** [API 2] 路径规划，返回途经点序列（首点=起点，末点=终点）；失败返回空列表 */
    List<CarLatLng> planRoute(in CarLatLng start, in CarLatLng end);

    /**
     * [API 2] 以某个 POI 为终点开始导航。
     *
     * @return CarApi.RESULT_* 错误码。业务失败用返回码而不是异常，
     *         这样 RemoteException 的含义始终只有一个：链路断了。
     */
    int navigateTo(in CarPoi destination);

    /** [API 2] 停止导航（未在导航中时为空操作，直接返回） */
    void stopNavigation();

    /** [API 2] 当前导航状态；空闲时为 navigating=false 的实例，**不返回 null** */
    CarNavState getNavState();
}
