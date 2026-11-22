// ============================================================================
// 对外回调接口
//
// 必须是 oneway：普通（同步）回调会在客户端处理慢时反压服务端的 Binder 线程池，
// 而 Binder 线程池只有 16 个线程，卡住就会波及同一进程的所有 IPC。
// oneway 意味着「发出即返回」，回调里不要做耗时操作。
//
// 演进代价（比服务接口高，务必谨慎）：
// 往本接口追加方法后，**旧客户端 APK 仍能正常运行**（它不认识新的 transaction
// code，收不到新回调），但用新 AAR 重新编译旧源码会失败 —— 生成的 Stub 多了
// 抽象方法。所以回调接口应尽早定型，需要整套新语义时新建独立接口，
// 而不是长期往一个接口里塞方法。
// ============================================================================
package com.sky.automap.car.api;

import com.sky.automap.car.api.model.CarNavState;

oneway interface IRemoteCarCallback {

    /**
     * [API 1] 服务就绪。
     *
     * bindService 返回 true 不代表业务可用 —— 进程起来时地图 SDK / 基建层
     * 可能仍在初始化。调用方应把它当作「可以开始调用业务方法」的信号，
     * 而不是依赖 ServiceConnection.onServiceConnected。
     */
    void onServiceReady();

    /**
     * [API 2] 导航状态变化。
     *
     * 服务端只在状态**真正变化**时推送，不会周期性刷；外部方若要显示实时里程，
     * 仍需轮询 getNavState()。回调在 Binder 线程执行，不要做耗时操作。
     */
    void onNavStateChanged(in CarNavState state);
}
