package com.sky.demo.carclient;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.sky.automap.car.api.CarApi;
import com.sky.automap.car.api.IRemoteCarCallback;
import com.sky.automap.car.api.IRemoteCarService;
import com.sky.automap.car.api.model.CarIdentity;
import com.sky.automap.car.api.model.CarLatLng;
import com.sky.automap.car.api.model.CarNavState;
import com.sky.automap.car.api.model.CarPoi;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 外部应用调用 CarMap 的 AIDL 契约的最小示例。
 *
 * 这个类演示了跨进程调用的五个关键点，缺一个就「绑不上」或「绑上了调不通」：
 *
 * 1. **用 action 查找，不要写死包名**：对端 applicationId 按车型是
 *    com.sky.automap.h6 / .h9，且两个包可以同时安装。
 * 2. **manifest 必须声明 `<queries>`**：Android 11+ 不声明就查不到 service。
 * 3. **必须持有对端声明的 signature 权限**：联调阶段两边都用 debug 包即可。
 * 4. **bindService 成功 ≠ 业务就绪**：以 onServiceReady 回调为准。
 * 5. **必须处理 binder 死亡与注销**：DeathRecipient + unregisterCallback + unbind。
 *
 * 业务方法（API 2）演示：searchPoi / planRoute / navigateTo / stopNavigation / getNavState，
 * 以及服务端主动推送的 onNavStateChanged。
 */
public class MainActivity extends Activity {

    private static final String TAG = "CarClientDemo";

    /** 与 CarApi.ACTION_SERVICE 是同一个值；代码里用常量，避免手打错 */
    private static final String SERVICE_ACTION = CarApi.ACTION_SERVICE;

    /** 示例中心点（上海人民广场），真实业务里应由外部方自己决定 */
    private static final CarLatLng DEMO_CENTER = new CarLatLng(31.2304, 121.4737);

    private TextView statusView;
    private TextView logView;
    private ScrollView logScroll;

    /** 跨进程代理；null 表示尚未连接 */
    private IRemoteCarService remote;
    private boolean bound;

    /**
     * 对端能力版本（getApiVersion 的返回值）。
     * 0 = 还没问过；调 API 2 的方法前必须 >= 2。
     */
    private int remoteApiVersion;

    /** 上一次搜索结果，供「导航到第一个」使用 */
    private List<CarPoi> lastPois = Collections.emptyList();

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /**
     * 服务端回调。注意这是 oneway 的，回调发生在 **Binder 线程**，
     * 更新 UI 必须切回主线程（runOnUiThread）。
     */
    private final IRemoteCarCallback callback = new IRemoteCarCallback.Stub() {
        @Override
        public void onServiceReady() {
            post("回调 onServiceReady —— 服务端业务已就绪，可以开始调用业务方法");
        }

        @Override
        public void onNavStateChanged(CarNavState state) {
            post("回调 onNavStateChanged: " + state);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            bound = true;
            remote = IRemoteCarService.Stub.asInterface(binder);
            post("onServiceConnected: " + name.flattenToShortString());
            updateStatus();

            watchBinderDeath(binder);

            // 连上就立刻查一下身份，确认对端是我们预期的那一个（H6 还是 H9）
            queryIdentity();

            // 主动注册回调：如果服务端此刻已就绪，会立刻收到一次 onServiceReady
            registerCallback();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // 服务端进程崩溃/被杀会走这里（进程还在只是断连的情况很少），
            // 系统会自动重连，所以这里只需要把代理置空并提示。
            bound = false;
            remote = null;
            post("onServiceDisconnected（服务端进程可能已崩溃，系统会自动尝试重连）");
            updateStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        updateStatus();
        log("已启动。步骤：查找并绑定 → 查询身份 → 搜索 POI → 开始导航。");
        checkPermission();
    }

    // ------------------------------------------------------------------
    // 连接
    // ------------------------------------------------------------------

    /** 第 1 步：按 action 查，再用查到的组件名绑定 */
    private void findAndBind() {
        if (bound) {
            log("已经绑定过了");
            return;
        }

        Intent query = new Intent(SERVICE_ACTION);
        List<ResolveInfo> services = getPackageManager().queryIntentServices(query, 0);

        log("queryIntentServices(\"" + SERVICE_ACTION + "\") → " + services.size() + " 个实现");
        if (services.isEmpty()) {
            log("查不到。检查三件事：1) 对端已安装；2) 本应用 manifest 里有 <queries>；"
                    + "3) action 字符串一致。");
            return;
        }

        // H6 与 H9 两个包可同时安装 → 可能命中多个，逐个打出来
        for (ResolveInfo info : services) {
            ServiceInfo si = info.serviceInfo;
            log("  发现 " + si.packageName + " / " + si.name);
        }

        ServiceInfo target = services.get(0).serviceInfo;
        Intent intent = new Intent(SERVICE_ACTION)
                .setComponent(new ComponentName(target.packageName, target.name));

        try {
            boolean ok = bindService(intent, connection, Context.BIND_AUTO_CREATE);
            log("bindService → " + ok + "（true 只代表「绑定请求已被受理」，业务是否可用看 onServiceReady）");
            if (!ok) {
                log("常见原因：权限被拒（签名不一致）或对端 service 未导出。");
            }
        } catch (SecurityException e) {
            log("bindService 抛 SecurityException：" + e.getMessage()
                    + " → 本应用没拿到 " + CarApi.PERMISSION_BIND + "，需与对端同签名");
        }
    }

    private void unbind() {
        if (!bound) {
            log("当前未绑定");
            return;
        }
        // 顺序很重要：先注销回调，再解绑，否则服务端会一直持有本进程的 binder 引用
        try {
            if (remote != null) {
                remote.unregisterCallback(callback);
            }
        } catch (RemoteException e) {
            log("unregisterCallback 失败（对端已死，可忽略）：" + e.getMessage());
        }
        try {
            unbindService(connection);
        } catch (IllegalArgumentException e) {
            log("unbindService 失败：" + e.getMessage());
        }
        bound = false;
        remote = null;
        log("已解绑");
        updateStatus();
    }

    // ------------------------------------------------------------------
    // 基础接口（API 1）
    // ------------------------------------------------------------------

    private void queryIdentity() {
        if (!ensureBound()) {
            return;
        }
        try {
            remoteApiVersion = remote.getApiVersion();
            log("getApiVersion() = " + remoteApiVersion
                    + "（契约能力版本，与 Maven 版本无关；调新方法前先看它）");

            CarIdentity identity = remote.getIdentity();
            if (identity == null) {
                log("getIdentity() 返回 null —— 按契约不应发生");
                return;
            }
            log("getIdentity(): applicationId=" + identity.getApplicationId()
                    + ", carModel=" + identity.getCarModel()
                    + ", channelId=" + identity.getChannelId()
                    + ", mapVendor=" + identity.getMapVendor());
        } catch (RemoteException e) {
            log("调用失败 RemoteException：" + e.getMessage() + "（对端进程可能已死）");
        } catch (SecurityException e) {
            log("调用被拒 SecurityException：" + e.getMessage());
        }
    }

    private void registerCallback() {
        if (!ensureBound()) {
            return;
        }
        try {
            remote.registerCallback(callback);
            log("registerCallback 已提交（若服务端已就绪会立刻回调 onServiceReady）");
        } catch (RemoteException e) {
            log("registerCallback 失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 业务接口（API 2）
    // ------------------------------------------------------------------

    private void searchPoi() {
        if (!ensureApiLevel(2, "搜索 POI")) {
            return;
        }
        try {
            List<CarPoi> pois = remote.searchPoi("充电站", DEMO_CENTER);
            lastPois = pois != null ? pois : Collections.<CarPoi>emptyList();

            log("searchPoi(\"充电站\") → " + lastPois.size() + " 条");
            if (lastPois.isEmpty()) {
                log("  空结果可能是「没搜到」，也可能是服务端地图未就绪 —— 契约里两者都返回空列表。");
                return;
            }
            int show = Math.min(lastPois.size(), 3);
            for (int i = 0; i < show; i++) {
                CarPoi poi = lastPois.get(i);
                log("  #" + (i + 1) + " " + poi.getName() + " | " + poi.getAddress()
                        + " (" + poi.getLatitude() + ", " + poi.getLongitude() + ")");
            }
        } catch (RemoteException e) {
            log("searchPoi 失败： " + e.getMessage());
        }
    }

    private void planRoute() {
        if (!ensureApiLevel(2, "规划路线")) {
            return;
        }
        try {
            CarLatLng end = new CarLatLng(31.2400, 121.4900);
            List<CarLatLng> points = remote.planRoute(DEMO_CENTER, end);
            int size = points != null ? points.size() : 0;
            log("planRoute → " + size + " 个途经点（首点=起点，末点=终点）");
            if (size > 0) {
                log("  起点 " + points.get(0) + " → 终点 " + points.get(size - 1));
            }
        } catch (RemoteException e) {
            log("planRoute 失败：" + e.getMessage());
        }
    }

    private void navigateToFirstPoi() {
        if (!ensureApiLevel(2, "导航")) {
            return;
        }
        if (lastPois.isEmpty()) {
            log("先点「搜索 POI」拿到候选，再导航");
            return;
        }
        CarPoi destination = lastPois.get(0);
        try {
            int result = remote.navigateTo(destination);
            log("navigateTo(" + destination.getName() + ") → result=" + result
                    + "（" + describe(result) + "）");
            // 状态变化会通过 onNavStateChanged 推回来，这里不再主动查一次
        } catch (RemoteException e) {
            log("navigateTo 失败：" + e.getMessage());
        }
    }

    private void stopNavigation() {
        if (!ensureApiLevel(2, "停止导航")) {
            return;
        }
        try {
            remote.stopNavigation();
            log("stopNavigation 已调用（状态变化同样会走 onNavStateChanged）");
        } catch (RemoteException e) {
            log("stopNavigation 失败：" + e.getMessage());
        }
    }

    private void queryNavState() {
        if (!ensureApiLevel(2, "查询导航状态")) {
            return;
        }
        try {
            CarNavState state = remote.getNavState();
            log("getNavState(): " + state);
            if (state != null && !state.getNavigating()) {
                log("  空闲态：契约保证不返回 null，用 navigating=false 表示");
            }
        } catch (RemoteException e) {
            log("getNavState 失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void checkPermission() {
        boolean granted = checkSelfPermission(CarApi.PERMISSION_BIND) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            log("权限 " + CarApi.PERMISSION_BIND + " 已授予");
        } else {
            log("权限 " + CarApi.PERMISSION_BIND + " 未授予 —— 对端是 signature 级权限，"
                    + "本 demo 必须与对端同签名（联调时两边都装 debug 包）。");
        }
    }

    private boolean ensureBound() {
        if (remote == null) {
            log("还没绑定，先点「查找并绑定」");
            return false;
        }
        return true;
    }

    /**
     * API 能力判断——**这是 getApiVersion() 存在的唯一理由**。
     *
     * 车机上装的可能是旧版 APK（不认识新方法）。直接调新方法的后果是：
     * AIDL Proxy 发一个旧服务端不认识的 transaction code，旧 Stub 的 onTransact
     * 落到 default 返回 false，调用方拿到 "Transaction failed" —— 报错里既没有
     * 「对端版本太老」也没有「哪个方法」，只能靠猜。所以调用前先问版本。
     *
     * @param required 该能力从哪个 API 版本开始可用（见 CarApi.API_VERSION）
     * @param feature  只用于日志，让降级原因一目了然
     */
    private boolean ensureApiLevel(int required, String feature) {
        if (!ensureBound()) {
            return false;
        }
        if (remoteApiVersion <= 0) {
            // 还没问过（例如 queryIdentity 曾抛过异常），补一次
            refreshApiVersion();
        }
        if (remoteApiVersion < required) {
            log(feature + " 需要 API " + required + "，对端只到 API " + remoteApiVersion
                    + " → 降级：跳过本次调用");
            return false;
        }
        return true;
    }

    private void refreshApiVersion() {
        try {
            remoteApiVersion = remote.getApiVersion();
            log("refreshApiVersion() = " + remoteApiVersion);
        } catch (RemoteException e) {
            remoteApiVersion = 0;
            log("getApiVersion 失败：" + e.getMessage());
        }
    }

    /** 把返回码翻译成人话——外部方应该按 CarApi.RESULT_* 分段处理，而不是只看非零 */
    private String describe(int result) {
        return switch (result) {
            case CarApi.RESULT_OK -> "成功";
            case CarApi.RESULT_INVALID_ARGUMENT -> "参数错误";
            case CarApi.RESULT_NOT_READY -> "服务未就绪，等 onServiceReady";
            case CarApi.RESULT_MAP_UNAVAILABLE -> "地图能力不可用";
            case CarApi.RESULT_INTERNAL_ERROR -> "服务端内部错误";
            default -> "未知返回码";
        };
    }

    private void watchBinderDeath(IBinder binder) {
        try {
            binder.linkToDeath(() -> {
                bound = false;
                remote = null;
                post("服务端进程已死亡（binderDied），下次点「查找并绑定」会重新拉起它");
                updateStatus();
            }, 0);
        } catch (RemoteException e) {
            log("linkToDeath 失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // UI（纯代码构建，避免 demo 引入 layout 资源）
    // ------------------------------------------------------------------

    private ViewGroup buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        root.addView(statusView, fullWidthWrap());

        LinearLayout row1 = horizontalRow();
        row1.addView(button("查找并绑定", v -> findAndBind()), weighted());
        row1.addView(button("查询身份", v -> queryIdentity()), weighted());
        row1.addView(button("解绑", v -> unbind()), weighted());
        root.addView(row1, fullWidthWrap());

        LinearLayout row2 = horizontalRow();
        row2.addView(button("搜索 POI", v -> searchPoi()), weighted());
        row2.addView(button("规划路线", v -> planRoute()), weighted());
        row2.addView(button("注册回调", v -> registerCallback()), weighted());
        root.addView(row2, fullWidthWrap());

        LinearLayout row3 = horizontalRow();
        row3.addView(button("导航到首个", v -> navigateToFirstPoi()), weighted());
        row3.addView(button("停止导航", v -> stopNavigation()), weighted());
        row3.addView(button("查导航态", v -> queryNavState()), weighted());
        root.addView(row3, fullWidthWrap());

        LinearLayout row4 = horizontalRow();
        row4.addView(button("检查权限", v -> checkPermission()), weighted());
        row4.addView(button("清空日志", v -> logView.setText("")), weighted());
        root.addView(row4, fullWidthWrap());

        logScroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextIsSelectable(true);
        logScroll.addView(logView);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        logParams.topMargin = dp(12);
        root.addView(logScroll, logParams);

        return root;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button button(String text, OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateStatus() {
        if (statusView == null) {
            return;
        }
        statusView.setText(bound
                ? "状态：已绑定（" + (remote != null ? "代理就绪" : "代理为空") + "）"
                : "状态：未绑定");
    }

    private void log(String line) {
        Log.i(TAG, line);
        if (logView == null) {
            return;
        }
        // 从 UI 线程直接调用（按钮回调）与从 Binder 线程调用（post）都走这里，
        // runOnUiThread 在 UI 线程上是立即执行，不会多绕一圈。
        appendLine(line);
    }

    /** Binder 线程 → 主线程的统一入口 */
    private void post(String line) {
        Log.i(TAG, line);
        runOnUiThread(() -> appendLine(line));
    }

    private void appendLine(String line) {
        if (logView == null) {
            return;
        }
        logView.append("[" + timeFormat.format(new Date()) + "] " + line + "\n");
        logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        unbind();
        super.onDestroy();
    }
}
