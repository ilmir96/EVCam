package com.kooo.evcam;

import android.app.ActivityManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Size;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.kooo.evcam.camera.MultiCameraManager;
import com.kooo.evcam.camera.SingleCamera;

/**
 * 补盲选项Сервис
 * 负责управление主屏悬浮窗 и 副屏显示
 */
public class BlindSpotService extends Service {
    private static final String TAG = "BlindSpotService";
    private static BlindSpotService sInstance;

    private WindowManager secondaryWindowManager;
    private View secondaryFloatingView;
    private TextureView secondaryTextureView;
    private Surface secondaryCachedSurface;
    private View secondaryBorderView;
    private SingleCamera secondaryCamera;
    private String secondaryDesiredCameraPos = null; // 目标副屏КамераПозиция

    private MainFloatingWindowView mainFloatingWindowView;
    private BlindSpotFloatingWindowView dedicatedBlindSpotWindow;
    private BlindSpotFloatingWindowView previewBlindSpotWindow;
    private boolean isMainTempShown = false; //  否为主屏временно显示
    private boolean isSecondaryAdjustMode = false;
    private int secondaryAttachedDisplayId = -1;

    private LogcatSignalObserver logcatSignalObserver;
    private VhalSignalObserver vhalSignalObserver;
    private CarSignalManagerObserver carSignalManagerObserver;
    private DoorSignalObserver doorSignalObserver; // 车门联动观察者
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private Runnable hideRunnable;
    private Runnable signalKeepAliveRunnable; // 信号保活计时器（debounce)
    private static final long SIGNAL_KEEPALIVE_MS = 1200; // 1.2 сек.无信号视为转 к 灯Закрыто（约3 шт.闪烁周期)
    private String currentSignalCamera = null; // Текущий转 к 灯触发 Камера
    private Runnable secondaryRetryRunnable;
    private int secondaryRetryCount = 0;
    private String previewCameraPos = null;

    private AppConfig appConfig;
    private DisplayManager displayManager;

    // 全景影像避让
    private Runnable avmCheckRunnable;
    private boolean isAvmAvoidanceActive = false; // Текущий 否处于避让Статус（AVMили自身Передний план)
    private int avmDeactivateCount = 0; // 连续Не ОбнаруженоAVMПередний план  раз数（去抖)
    private static final int AVM_DEACTIVATE_THRESHOLD = 2; // 连续2 раз（2 сек.)Не Обнаружено才解除避让
    private static final long AVM_CHECK_INTERVAL_MS = 1000; // Передний план检测轮询间隔
    private static volatile boolean isSelfInForeground = false; // EVCam自身Activity 否 Передний план（生命周期驱动)

    /** MainActivity.onResume 时调用 */
    public static void notifySelfForeground() {
        isSelfInForeground = true;
    }

    /** MainActivity.onPause 时调用 */
    public static void notifySelfBackground() {
        isSelfInForeground = false;
    }

    /**
     * проверка 否有活跃 Камера悬浮窗（补盲悬浮窗、常驻悬浮窗、副屏)Выполняется использованиеКамера。
     * 用于 MainActivity.onPause() 判断 否应该保持КамераПодключение。
     */
    public static boolean hasActiveCameraWindows() {
        BlindSpotService inst = sInstance;
        if (inst == null) return false;
        return inst.mainFloatingWindowView != null
                || inst.secondaryFloatingView != null
                || inst.dedicatedBlindSpotWindow != null;
    }

    // 定制键唤醒
    private boolean isCustomKeyPreviewShown = false; // 定制键唤醒 预览 否显示

    private WindowManager mockControlWindowManager;
    private View mockControlView;
    private WindowManager.LayoutParams mockControlParams;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        appConfig = new AppConfig(this);
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        initSignalObserver();
        initAvmAvoidance();
        initCustomKeyWakeup();
    }

    private void initSignalObserver() {
        // Остановка旧 观察者
        stopSignalObservers();

        String mode = appConfig.getTurnSignalTriggerMode();
        if (appConfig.isCarSignalManagerTriggerMode()) {
            initCarSignalManagerObserver();
        } else if (appConfig.isVhalGrpcTriggerMode()) {
            initVhalSignalObserver();
        } else {
            initLogcatSignalObserver();
        }
        
        // 车门联动（独立于转 к 灯联动)
        if (appConfig.isDoorLinkageEnabled()) {
            initDoorSignalObserver();
        }
    }

    /**
     * проверка信号观察者 否存活，若死亡则重新инициализация。
     * 由 onStartCommand（т.е. update())调用，修复观察者因Подключениеотключено、
     * инициализацияОшибка等原因静默死亡后无法自愈 问题。
     */
    private void ensureSignalObserversAlive() {
        if (!appConfig.isBlindSpotGlobalEnabled() && !appConfig.isCustomKeyWakeupEnabled()) return;
        if (!appConfig.isTurnSignalLinkageEnabled() && !appConfig.isDoorLinkageEnabled()
                && !appConfig.isCustomKeyWakeupEnabled()) return;

        boolean needReinit = false;
        if (appConfig.isCarSignalManagerTriggerMode()) {
            if (carSignalManagerObserver == null || !carSignalManagerObserver.isAlive()) {
                AppLog.w(TAG, "CarSignalManager observer dead, reinitializing");
                needReinit = true;
            }
        } else if (appConfig.isVhalGrpcTriggerMode()) {
            if (vhalSignalObserver == null || !vhalSignalObserver.isAlive()) {
                AppLog.w(TAG, "VHAL observer dead, reinitializing");
                needReinit = true;
            }
        } else {
            if (logcatSignalObserver == null || !logcatSignalObserver.isAlive()) {
                AppLog.w(TAG, "Logcat observer dead, reinitializing");
                needReinit = true;
            }
        }

        if (needReinit) {
            initSignalObserver();
        }
    }

    private void initVhalSignalObserver() {
        AppLog.d(TAG, "Using vehicle API trigger mode");

        vhalSignalObserver = new VhalSignalObserver(new VhalSignalObserver.TurnSignalListener() {
            @Override
            public void onTurnSignal(String direction, boolean on) {
                if (!appConfig.isBlindSpotGlobalEnabled()) return;
                if (!appConfig.isTurnSignalLinkageEnabled()) return;

                if (on) {
                    handleTurnSignal(direction);
                } else {
                    // 转 к 灯Закрыто，Запуск隐藏计时器
                    startHideTimer();
                }
            }

            @Override
            public void onConnectionStateChanged(boolean connected) {
                AppLog.d(TAG, "Vehicle API connection: " + (connected ? "connected" : "disconnected"));
            }
        });
        vhalSignalObserver.start();
    }

    private void initCarSignalManagerObserver() {
        AppLog.d(TAG, "Using CarSignalManager API trigger mode");

        carSignalManagerObserver = new CarSignalManagerObserver(this, new CarSignalManagerObserver.TurnSignalListener() {
            @Override
            public void onTurnSignal(String direction, boolean on) {
                if (!appConfig.isBlindSpotGlobalEnabled()) return;
                if (!appConfig.isTurnSignalLinkageEnabled()) return;

                if (on) {
                    //handleTurnSignal(direction);
                    // 转 к 灯открыть，显示Камера
                    // 注意：不能调用 handleTurnSignal()，因为它会触发 resetSignalKeepAlive()
                    // CarSignalManager API 通过轮询Получение精确Статус，不необходимо debounce 机制
                    showBlindSpotCamera(direction);
                } else {
                    // 转 к 灯Закрыто，Запуск隐藏计时器
                    startHideTimer();
                }
            }

            @Override
            public void onConnectionStateChanged(boolean connected) {
                AppLog.d(TAG, "CarSignalManager connection: " + (connected ? "connected" : "disconnected"));
            }
        });
        carSignalManagerObserver.start();
    }

    /**
     * инициализация车门联动观察者
     * - 车辆API режим（E5/星舰7): 复用有 信号观察者，Настройки DoorSignalListener
     * - CarSignalManager режим（L6/L7/博越L): использование独立  DoorSignalObserver
     */
    private void initDoorSignalObserver() {
        AppLog.i(TAG, "🚪 ========== Вкл始инициализация车门联动观察者 ==========");
        AppLog.i(TAG, "🚪 补盲функция总ВклВыкл: " + appConfig.isBlindSpotGlobalEnabled());
        AppLog.i(TAG, "🚪 车门联动ВклВыкл: " + appConfig.isDoorLinkageEnabled());
        AppLog.i(TAG, "🚪 车门联动Модель: " + appConfig.getTurnSignalPresetSelection() + " (复用转 к 联动конфигурация)");
        AppLog.i(TAG, "🚪 车门消失延迟: " + appConfig.getTurnSignalTimeout() + " сек. (复用转 к 联动конфигурация)");
        AppLog.i(TAG, "🚪 触发режим: " + appConfig.getTurnSignalTriggerMode());

        if (appConfig.isVhalGrpcTriggerMode()) {
            // E5/星舰7: 通过车辆API 监听车门Статус
            initVhalDoorSignalObserver();
        } else if (appConfig.isCarSignalManagerTriggerMode()) {
            // L6/L7/博越L: 通过 CarSignalManager API 监听车门Статус
            initCarSignalManagerDoorObserver();
        } else {
            AppLog.w(TAG, "🚪 Текущий触发режимне поддерживается车门联动: " + appConfig.getTurnSignalTriggerMode());
        }

        AppLog.i(TAG, "🚪 ========== 车门联动观察者инициализациязавершение ==========");
    }

    /**
     * 车辆API 车门联动（E5/星舰7)
     * 复用有 信号观察者Подключение，附加 DoorSignalListener
     */
    private void initVhalDoorSignalObserver() {
        AppLog.i(TAG, "� использование车辆API 车门联动 (E5/星舰7)");

        VhalSignalObserver.DoorSignalListener doorCallback = createDoorSignalCallback();

        if (vhalSignalObserver != null) {
            // 转 к 联动Запущено VhalSignalObserver，直接附加车门监听
            AppLog.i(TAG, "� 复用有 信号观察者，附加车门监听");
            vhalSignalObserver.setDoorSignalListener(doorCallback);
        } else {
            // 转 к 联动Не Запуск，необходимо单独创建 VhalSignalObserver（только用于车门)
            AppLog.i(TAG, "� 转 к 联动Не Запуск，创建信号观察者用于车门联动");
            vhalSignalObserver = new VhalSignalObserver(new VhalSignalObserver.TurnSignalListener() {
                @Override
                public void onTurnSignal(String direction, boolean on) {
                    // 转 к 联动Не Включить，忽略转 к 灯事件
                }
                @Override
                public void onConnectionStateChanged(boolean connected) {
                    AppLog.d(TAG, "车辆APIПодключение (door-only): " + (connected ? "connected" : "disconnected"));
                }
            });
            vhalSignalObserver.setDoorSignalListener(doorCallback);
            vhalSignalObserver.start();
        }
    }

    /**
     * CarSignalManager 车门联动（L6/L7/博越L)
     */
    private void initCarSignalManagerDoorObserver() {
        AppLog.i(TAG, "🚪 использование CarSignalManager API 车门联动 (L6/L7/博越L)");

        doorSignalObserver = new DoorSignalObserver(this, new DoorSignalObserver.DoorSignalListener() {
            @Override
            public void onDoorOpen(String side) {
                handleDoorOpen(side);
            }

            @Override
            public void onDoorClose(String side) {
                handleDoorClose(side);
            }

            @Override
            public void onConnectionStateChanged(boolean connected) {
                AppLog.i(TAG, "🚪 车门监听ПодключениеСтатус: " + (connected ? "✅ Подключено" : "❌ Не подключено"));
            }
        });

        doorSignalObserver.start();
    }

    /**
     * 创建车辆API 车门信号回调（复用相同 车门处理逻辑)
     */
    private VhalSignalObserver.DoorSignalListener createDoorSignalCallback() {
        return new VhalSignalObserver.DoorSignalListener() {
            @Override
            public void onDoorOpen(String side) {
                handleDoorOpen(side);
            }

            @Override
            public void onDoorClose(String side) {
                handleDoorClose(side);
            }

            @Override
            public void onConnectionStateChanged(boolean connected) {
                AppLog.i(TAG, "� 车辆API车门监听ПодключениеСтатус: " + (connected ? "✅ Подключено" : "❌ Не подключено"));
            }
        };
    }

    /**
     * 处理车门открыть事件（车辆API  и  CarSignalManager Всего 用)
     */
    private void handleDoorOpen(String side) {
        AppLog.i(TAG, "🚪🚪🚪 Получена команда: 车门открыть事件: " + side);

        if (!appConfig.isBlindSpotGlobalEnabled()) {
            AppLog.w(TAG, "🚪 补盲функцияНе Включить，跳过车门触发");
            return;
        }
        if (!appConfig.isDoorLinkageEnabled()) {
            AppLog.w(TAG, "🚪 车门联动Не Включить，跳过车门触发");
            return;
        }

        // Если Текущий有转 к 灯激活，车门联动让 кам.（转 к 灯优先级更Высокий)
        if (currentSignalCamera != null && !currentSignalCamera.isEmpty()) {
            AppLog.w(TAG, "🚪 转 к 灯Выполняется использование(" + currentSignalCamera + ")，车门联动让 кам.");
            return;
        }

        // Если 同侧Камера经 显示（车门联动触发 )，跳过重复显示
        if (isMainTempShown && mainFloatingWindowView != null) {
            AppLog.i(TAG, "🚪 车门联动Камера 显示，跳过重复创建");
            // 但необходимоОтмена隐藏计时器（门重新открыть)
            if (hideRunnable != null) {
                hideHandler.removeCallbacks(hideRunnable);
                hideRunnable = null;
                AppLog.i(TAG, "🚪 Отмена隐藏计时器（门重新открыть)");
            }
            return;
        }

        AppLog.i(TAG, "🚪 ✅ 车门открыть: " + side + "，准备显示Камера");
        showDoorCamera(side);
    }

    /**
     * 处理车门Закрыто事件（车辆API  и  CarSignalManager Всего 用)
     */
    private void handleDoorClose(String side) {
        AppLog.i(TAG, "🚪🚪🚪 Получена команда: 车门Закрыто事件: " + side);

        if (!appConfig.isDoorLinkageEnabled()) {
            AppLog.w(TAG, "🚪 车门联动Не Включить，跳过Закрыто逻辑");
            return;
        }

        // 只有 没有转 к 灯激活时才Закрыто车门Камера
        if (currentSignalCamera != null && !currentSignalCamera.isEmpty()) {
            AppLog.w(TAG, "🚪 转 к 灯Выполняется использование(" + currentSignalCamera + ")，不Закрыто车门Камера");
            return;
        }

        // проверка 否有车门联动触发 窗口 显示
        if (!isMainTempShown && dedicatedBlindSpotWindow == null) {
            AppLog.i(TAG, "🚪 没有车门联动窗口 显示，跳过Закрыто逻辑");
            return;
        }

        AppLog.i(TAG, "🚪 ✅ 车门Закрыто: " + side + "，准备延迟ЗакрытоКамера");
        startDoorHideTimer();
    }

    private void initLogcatSignalObserver() {
        AppLog.d(TAG, "Using Logcat trigger mode");

        // 安全兜底：т.е.使 logcat -T  от 源头跳过历史缓冲，
        // 仍保留 500ms 预热期以防极端情况（еслиСистема时间跳变)
        final long observerStartTime = System.currentTimeMillis();
        final long WARMUP_MS = 500;

        logcatSignalObserver = new LogcatSignalObserver((line, data1) -> {
            if (System.currentTimeMillis() - observerStartTime < WARMUP_MS) return;

            if (!appConfig.isBlindSpotGlobalEnabled()) return;
            if (!appConfig.isTurnSignalLinkageEnabled()) return;

            String leftKeyword = appConfig.getTurnSignalLeftTriggerLog();
            String rightKeyword = appConfig.getTurnSignalRightTriggerLog();

            boolean matched = false;
            if (leftKeyword != null && !leftKeyword.isEmpty() && line.contains(leftKeyword)) {
                matched = true;
                hideHandler.post(() -> handleTurnSignal("left"));
            } else if (rightKeyword != null && !rightKeyword.isEmpty() && line.contains(rightKeyword)) {
                matched = true;
                hideHandler.post(() -> handleTurnSignal("right"));
            }

            if (matched) return;

            if (line.contains("left front turn signal:0") && line.contains("right front turn signal:0")) {
                hideHandler.post(this::startHideTimer);
                return;
            }

            if (line.contains("data1 = 0") || data1 == 0) {
                hideHandler.post(this::startHideTimer);
                return;
            }
        });
        // 将用户конфигурация 触发Выкл键字传入，用于构建 logcat -e 原生过滤正则。
        // 行驶车机 д.志量暴增，不做原生过滤会导致转 к 灯信号 "淹没"而延迟。
        logcatSignalObserver.setFilterKeywords(
                appConfig.getTurnSignalLeftTriggerLog(),
                appConfig.getTurnSignalRightTriggerLog()
        );
        logcatSignalObserver.start();
    }

    private void stopSignalObservers() {
        if (logcatSignalObserver != null) {
            logcatSignalObserver.stop();
            logcatSignalObserver = null;
        }
        if (vhalSignalObserver != null) {
            vhalSignalObserver.setDoorSignalListener(null); // очистка车门监听
            vhalSignalObserver.setCustomKeyListener(null); // очистка定制键监听
            vhalSignalObserver.stop();
            vhalSignalObserver = null;
        }
        if (carSignalManagerObserver != null) {
            carSignalManagerObserver.stop();
            carSignalManagerObserver = null;
        }
        if (doorSignalObserver != null) {
            doorSignalObserver.stop();
            doorSignalObserver = null;
        }
    }

    /**
     * 显示盲区Камера（用于 CarSignalManager API，不использование debounce)
     */
    private void showBlindSpotCamera(String cameraPos) {
        // 全景影像避让：目标Activity Передний план时不弹出补盲窗口
        if (isAvmAvoidanceActive) {
            AppLog.d(TAG, "全景影像避让，忽略CarSignalManager转 к 灯信号: " + cameraPos);
            return;
        }

        AppLog.i(TAG, "🚦 转 к 灯触发Камера: " + cameraPos);
        
        // Если 车门联动窗口 显示，先Закрыто（转 к 灯优先级更Высокий)
        if (isMainTempShown) {
            AppLog.i(TAG, "🚦 Обнаружено车门联动窗口，转 к 灯接管（优先级更Высокий)");
            isMainTempShown = false;
        }
        
        // Отмена隐藏计时器
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
            hideRunnable = null;
            AppLog.d(TAG, "🚦 Отмена隐藏计时器");
        }

        // Отмена信号保活计时器（Если до от Другоережим切换过来)
        if (signalKeepAliveRunnable != null) {
            hideHandler.removeCallbacks(signalKeepAliveRunnable);
            signalKeepAliveRunnable = null;
        }

        if (cameraPos.equals(currentSignalCamera)) {
            AppLog.d(TAG, "转 к 灯相同，不重复切换: " + cameraPos);
            return;
        }

        currentSignalCamera = cameraPos;
        AppLog.i(TAG, "🚦 转 к 灯激活，Настройки currentSignalCamera = " + cameraPos);

        // --- 1. 尽早创建窗口 UI（addView 触发布局， и 后续 IPC 并行，Surface 绪更快) ---
        boolean reuseMain = appConfig.isTurnSignalReuseMainFloating();

        if (reuseMain) {
            // 复用主屏悬浮窗
            if (mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
            }
            if (WakeUpHelper.hasOverlayPermission(this)) {
                mainFloatingWindowView = new MainFloatingWindowView(this, appConfig);
                mainFloatingWindowView.setDesiredCamera(cameraPos, true);
                mainFloatingWindowView.show();
                mainFloatingWindowView.updateStatusLabel(cameraPos);
                isMainTempShown = true;
                AppLog.d(TAG, "主屏Вкл启временно补盲悬浮窗");
            }
        } else {
            // использование独立补盲悬浮窗
            if (mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
                isMainTempShown = false;
            }
            if (dedicatedBlindSpotWindow != null) {
                dedicatedBlindSpotWindow.dismiss();
                dedicatedBlindSpotWindow = null;
            }
            dedicatedBlindSpotWindow = new BlindSpotFloatingWindowView(this, false);
            dedicatedBlindSpotWindow.setCameraPos(cameraPos);
            dedicatedBlindSpotWindow.show();
            dedicatedBlindSpotWindow.updateStatusLabel(cameraPos);
            // setCamera необходимо CameraManager，延后 до инициализацияпосле调用
        }

        // 副屏窗口预创建（addView 触发布局)
        if (appConfig.isSecondaryDisplayEnabled()) {
            if (secondaryFloatingView == null) {
                showSecondaryDisplay();
            }
        }

        // --- 2. 异步ЗапускПередний планСервис и инициализация相机（ и  UI 布局并行) ---
        CameraForegroundService.start(this, "Слепые зоны активны", "Отображение мониторинга слепых зон");
        com.kooo.evcam.camera.CameraManagerHolder.getInstance().getOrInit(this);

        // --- 3. 提前открыть相机（ и  Surface 创建并行，节省 ~20-60ms) ---
        {
            MultiCameraManager cm = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
            if (cm != null) {
                SingleCamera cam = cm.getCamera(cameraPos);
                if (cam != null && !cam.isCameraOpened()) {
                    CameraForegroundService.whenReady(this, cam::openCameraDeferred);
                }
            }
        }

        // --- 4. необходимо CameraManager  операция ---
        if (!reuseMain && dedicatedBlindSpotWindow != null) {
            dedicatedBlindSpotWindow.setCamera(cameraPos);
        }

        // 副屏Камера预览
        if (appConfig.isSecondaryDisplayEnabled()) {
            startSecondaryCameraPreviewDirectly(cameraPos);
        }
    }

    private void handleTurnSignal(String cameraPos) {
        // Отмена隐藏计时器
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
            hideRunnable = null;
        }

        // Сброс信号保活计时器（debounce)
        // 每 разПолучена команда: действует信号（value:1)всеСброс，超过 1.2  сек.无新信号则认为转 к 灯Закрыто
        resetSignalKeepAlive();

        if (cameraPos.equals(currentSignalCamera)) {
            AppLog.d(TAG, "转 к 灯相同，不重复切换: " + cameraPos);
            return;
        }

        currentSignalCamera = cameraPos;
        AppLog.d(TAG, "转 к 灯触发Камера: " + cameraPos);

        // --- 1. 尽早创建窗口 UI（addView 触发布局， и 后续 IPC 并行，Surface 绪更快) ---
        boolean reuseMain = false;
        // 全景影像避让：目标Activity Передний план时只跳过主屏窗口，副屏仍нормально工作
        if (!isAvmAvoidanceActive) {
            reuseMain = appConfig.isTurnSignalReuseMainFloating();

            if (reuseMain) {
                // --- 复用主屏悬浮窗逻辑 ---
                // 切换方 к 时重建悬浮窗，确保窗口尺寸/Поворот 参数 и 新Камера匹配
                if (mainFloatingWindowView != null) {
                    mainFloatingWindowView.dismiss();
                    mainFloatingWindowView = null;
                }
                if (WakeUpHelper.hasOverlayPermission(this)) {
                    mainFloatingWindowView = new MainFloatingWindowView(this, appConfig);
                    mainFloatingWindowView.setDesiredCamera(cameraPos, true);
                    mainFloatingWindowView.show();
                    mainFloatingWindowView.updateStatusLabel(cameraPos);
                    isMainTempShown = true;
                    AppLog.d(TAG, "主屏Вкл启временно补盲悬浮窗");
                }
            } else {
                // --- использование独立补盲悬浮窗逻辑 ---
                // 切换方 к 时重建悬浮窗
                if (mainFloatingWindowView != null) {
                    mainFloatingWindowView.dismiss();
                    mainFloatingWindowView = null;
                    isMainTempShown = false;
                }
                if (dedicatedBlindSpotWindow != null) {
                    dedicatedBlindSpotWindow.dismiss();
                    dedicatedBlindSpotWindow = null;
                }
                dedicatedBlindSpotWindow = new BlindSpotFloatingWindowView(this, false);
                dedicatedBlindSpotWindow.setCameraPos(cameraPos); // 先НастройкиКамераПозиция，再 show
                dedicatedBlindSpotWindow.show();
                dedicatedBlindSpotWindow.updateStatusLabel(cameraPos);
                // setCamera необходимо CameraManager，延后 до инициализацияпосле调用
            }
        } else {
            AppLog.d(TAG, "全景影像避让，跳过主屏窗口创建，副屏нормально处理: " + cameraPos);
        }

        // --- 副屏窗口预创建（addView 触发布局) ---
        if (appConfig.isSecondaryDisplayEnabled()) {
            if (secondaryFloatingView == null) {
                showSecondaryDisplay();
            }
        }

        // --- 2. 异步ЗапускПередний планСервис и инициализация相机（ и  UI 布局并行) ---
        // Передний планСервис Фоновый режимдоступКамера 前提条件，但 addView 不необходимо它
        // 冷Запуск时 CameraForegroundService 可能还Не Запуск，导致Камера Система CAMERA_DISABLED 拦截
        CameraForegroundService.start(this, "Слепые зоны активны", "Отображение мониторинга слепых зон");

        // 确保Камераинициализация（通过全局 Holder，不依赖 MainActivity)
        com.kooo.evcam.camera.CameraManagerHolder.getInstance().getOrInit(this);

        // --- 3. 提前открыть相机（ и  Surface 创建并行，节省 ~20-60ms) ---
        {
            MultiCameraManager cm = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
            if (cm != null) {
                SingleCamera cam = cm.getCamera(cameraPos);
                if (cam != null && !cam.isCameraOpened()) {
                    CameraForegroundService.whenReady(this, cam::openCameraDeferred);
                }
            }
        }

        // --- 4. необходимо CameraManager  операция ---
        // dedicatedBlindSpotWindow.setCamera() необходимо CameraManager Получение previewSize
        if (!isAvmAvoidanceActive && !reuseMain && dedicatedBlindSpotWindow != null) {
            dedicatedBlindSpotWindow.setCamera(cameraPos);
        }

        // --- 副屏Камера预览 ---
        if (appConfig.isSecondaryDisplayEnabled()) {
            startSecondaryCameraPreviewDirectly(cameraPos);
        }
    }

    private void startSecondaryCameraPreviewDirectly(String cameraPos) {
        secondaryDesiredCameraPos = cameraPos;
        BlindSpotCorrection.apply(secondaryTextureView, appConfig, cameraPos, appConfig.getSecondaryDisplayRotation());
        MultiCameraManager cameraManager = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
        if (cameraManager == null) {
            scheduleSecondaryRetry(cameraPos);
            return;
        }

        SingleCamera newCamera = cameraManager.getCamera(cameraPos);
        if (newCamera == null) {
            scheduleSecondaryRetry(cameraPos);
            return;
        }
        
        boolean surfaceReady = secondaryTextureView != null && secondaryTextureView.isAvailable()
            && secondaryCachedSurface != null && secondaryCachedSurface.isValid();
        if (newCamera == secondaryCamera && surfaceReady && newCamera.isSecondaryDisplaySurfaceBound(secondaryCachedSurface)) {
            cancelSecondaryRetry();
            AppLog.d(TAG, "副屏КамераНе 变化且 Surface 绑定，跳过 Session 重建: " + cameraPos);
            return;
        }

        cancelSecondaryRetry();
        boolean isSwitchingCamera = secondaryCamera != null && secondaryCamera != newCamera;
        if (isSwitchingCamera) {
            stopSecondaryCameraPreview();
        }
        secondaryCamera = newCamera;
        
        if (secondaryCamera != null && secondaryTextureView != null && secondaryTextureView.isAvailable()) {
            if (secondaryCachedSurface == null || !secondaryCachedSurface.isValid()) {
                Size previewSize = secondaryCamera.getPreviewSize();
                if (previewSize == null || !secondaryCamera.isCameraOpened()) {
                    // 相机Не открыть：注册一 раз性回调，相机открыть时立т.е.绑定（无需轮询)
                    // 回调  onOpened   backgroundHandler 线程同步выполнение，
                    //   createCameraPreviewSession дозавершение，确保副屏 Surface  Первый раз Session содержит
                    AppLog.d(TAG, "副屏注册 onCameraOpened 回调ожидание绑定: " + cameraPos);
                    cancelSecondaryRetry();
                    final SingleCamera cam = secondaryCamera;
                    final TextureView tv = secondaryTextureView;
                    cam.addOnCameraOpenedCallback(() -> {
                        Size ps = cam.getPreviewSize();
                        if (ps != null && tv != null && tv.isAvailable()) {
                            android.graphics.SurfaceTexture st = tv.getSurfaceTexture();
                            if (st != null) {
                                st.setDefaultBufferSize(ps.getWidth(), ps.getHeight());
                                if (secondaryCachedSurface != null) secondaryCachedSurface.release();
                                secondaryCachedSurface = new Surface(st);
                                cam.setSecondaryDisplaySurface(secondaryCachedSurface, st);
                                AppLog.d(TAG, "副屏通过 onCameraOpened 回调立т.е.绑定 Surface: " + cameraPos);
                            }
                        }
                    });
                    // Если 相机Не открыть，判断 否необходимо副屏主动открыть
                    // 当主屏悬浮窗Выполняется 创建时，由主屏  updateCamera() открыть相机，
                    // 这样 onCameraOpened 回调副屏 Surface 绪，session 一 раз建成无需重建
                    if (!cam.isCameraOpened()) {
                        boolean mainWindowWillOpenCamera = mainFloatingWindowView != null || dedicatedBlindSpotWindow != null;
                        if (!mainWindowWillOpenCamera) {
                            AppLog.d(TAG, "副屏主动открыть相机（无主屏窗口触发): " + cameraPos);
                            CameraForegroundService.whenReady(BlindSpotService.this, cam::openCamera);
                        } else {
                            AppLog.d(TAG, "副屏ожидание主屏窗口открыть相机（避免过早创建session): " + cameraPos);
                        }
                    }
                    return;
                }
                if (secondaryCachedSurface != null) secondaryCachedSurface.release();
                android.graphics.SurfaceTexture surfaceTexture = secondaryTextureView.getSurfaceTexture();
                if (surfaceTexture != null) {
                    surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                }
                secondaryCachedSurface = new Surface(secondaryTextureView.getSurfaceTexture());
            }
            
            if (isSwitchingCamera) {
                // 切换Камера：延迟绑定副屏 Surface，等旧 session 完全Закрыто释放 Surface
                // 主悬浮窗会先显示（不含副屏 Surface)，副屏稍后加入，避免 "connect: already connected"
                AppLog.d(TAG, "副屏延迟绑定 Surface（ожидание旧 session Закрыто): " + cameraPos);
                final SingleCamera delayedCamera = secondaryCamera;
                final Surface delayedSurface = secondaryCachedSurface;
                hideHandler.postDelayed(() -> {
                    // Подтвердить仍然 同一 шт.Камера и  Surface（防止快速切换导致 истекло回调)
                    if (delayedCamera == secondaryCamera && delayedSurface == secondaryCachedSurface
                            && delayedSurface != null && delayedSurface.isValid()) {
                        AppLog.d(TAG, "副屏绑定 Surface 并重建 Session: " + cameraPos);
                        android.graphics.SurfaceTexture delaySt = (secondaryTextureView != null && secondaryTextureView.isAvailable()) ? secondaryTextureView.getSurfaceTexture() : null;
                        delayedCamera.setSecondaryDisplaySurface(delayedSurface, delaySt);
                        delayedCamera.recreateSession(false);
                    }
                }, 300);
            } else {
                // 同一 шт.Камераили首 раз绑定：立т.е.Настройки
                // 首 раз绑定：始终использование紧急режим
                // - 主屏通过 createCameraPreviewSession() 直接创建 session，不走 recreateSession，
                //   поэтомуне существует双 urgent 冲突
                // - urgent=true 时 isConfiguring=true   delay=50ms（vs 非紧急  500ms)，
                //   足够等主屏 session завершениеконфигурация后立т.е.重建
                AppLog.d(TAG, "副屏绑定新 Surface 并重建 Session: " + cameraPos + " (urgent=true)");
                android.graphics.SurfaceTexture secSt = (secondaryTextureView != null && secondaryTextureView.isAvailable()) ? secondaryTextureView.getSurfaceTexture() : null;
                secondaryCamera.setSecondaryDisplaySurface(secondaryCachedSurface, secSt);
                secondaryCamera.recreateSession(true);
            }
            BlindSpotCorrection.apply(secondaryTextureView, appConfig, cameraPos, appConfig.getSecondaryDisplayRotation());
        } else {
            AppLog.d(TAG, "副屏 TextureView 尚Не 绪，暂不绑定 Surface: " + cameraPos);
            scheduleSecondaryRetry(cameraPos);
        }
    }

    private void scheduleSecondaryRetry(String cameraPos) {
        cancelSecondaryRetry();
        secondaryRetryCount++;
        long delayMs;
        if (secondaryRetryCount <= 5) {
            // 前5 раз快速重试（50ms)，覆盖冷Запускожидание previewSize 位 场景
            delayMs = 50;
        } else if (secondaryRetryCount <= 15) {
            delayMs = 500;
        } else if (secondaryRetryCount <= 35) {
            delayMs = 1000;
        } else {
            delayMs = 3000;
        }
        secondaryRetryRunnable = () -> startSecondaryCameraPreviewDirectly(cameraPos);
        hideHandler.postDelayed(secondaryRetryRunnable, delayMs);
    }

    private void cancelSecondaryRetry() {
        if (secondaryRetryRunnable != null) {
            hideHandler.removeCallbacks(secondaryRetryRunnable);
            secondaryRetryRunnable = null;
        }
        secondaryRetryCount = 0;
    }

    /**
     * 预触发相机открыть（ и  UI 创建并行выполнение)。
     *  创建悬浮窗до调用，使 openCamera  异步операция и 窗口创建/布局同时进行，
     * 避免等 TextureView 绪后才串行触发 openCamera  延迟。
     * openCamera Внутреннее有 isOpening/isCameraOpened 防护，不会重复открыть。
     */
    private void preOpenCamera(String cameraPos) {
        MultiCameraManager cameraManager = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
        if (cameraManager == null) return;
        SingleCamera cam = cameraManager.getCamera(cameraPos);
        if (cam != null && !cam.isCameraOpened()) {
            AppLog.d(TAG, "预触发相机открыть（ и UI并行): " + cameraPos);
            CameraForegroundService.whenReady(this, cam::openCamera);
        }
    }

    /**
     * Сброс信号保活计时器（debounce 机制)
     * 转 к 灯闪烁时，每 ~400ms 会产生一 раз value:1   д.志。
     * Если 超过 1.2  сек.没有Получена команда: 新  value:1 信号，说明转 к 灯Закрыто，
     * 此时Запуск隐藏计时器（用户конфигурация 延迟时间)。
     */
    private void resetSignalKeepAlive() {
        if (signalKeepAliveRunnable != null) {
            hideHandler.removeCallbacks(signalKeepAliveRunnable);
        }
        signalKeepAliveRunnable = () -> {
            AppLog.d(TAG, "转 к 灯信号таймаут（" + SIGNAL_KEEPALIVE_MS + "ms 无新信号)，Запуск隐藏计时器");
            signalKeepAliveRunnable = null;
            startHideTimer();
        };
        hideHandler.postDelayed(signalKeepAliveRunnable, SIGNAL_KEEPALIVE_MS);
    }

    private void startHideTimer() {
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
        }

        int timeout = appConfig.getTurnSignalTimeout();
        AppLog.i(TAG, "🚦 转 к 灯熄灭，Запуск隐藏计时器: " + timeout + " сек.后ЗакрытоКамера");

        hideRunnable = () -> {
            AppLog.i(TAG, "🚦 ⏰ 转 к 灯таймаут(" + timeout + " сек.)，隐藏补盲画面");
            currentSignalCamera = null;
            AppLog.i(TAG, "🚦 очистка currentSignalCamera，车门联动ВосстановлениеДоступно");
            
            // Восстановление主屏悬浮窗Статус
            if (isMainTempShown && mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
                isMainTempShown = false;
            } else if (mainFloatingWindowView != null) {
                mainFloatingWindowView.updateCamera(appConfig.getMainFloatingCamera());
            }

            // 隐藏独立补盲窗
            if (dedicatedBlindSpotWindow != null) {
                dedicatedBlindSpotWindow.dismiss();
                dedicatedBlindSpotWindow = null;
                
                // Если 原本主屏悬浮窗 Вкл启 ，补盲завершить后необходимоВосстановление它
                if (appConfig.isMainFloatingEnabled()) {
                    updateMainFloatingWindow();
                }
            }

            // --- 副屏显示Восстановление ---
            updateSecondaryDisplay();
            hideRunnable = null;
            
            // 补盲завершить，Если 没有持久 Surface  用且 Activity  Фоновый режим，释放相机
            closeCamerasIfIdle();
        };

        hideHandler.postDelayed(hideRunnable, timeout * 1000L);
    }

    // ==================== 车门联动相Выкл方法 ====================
    
    /**
     * 显示车门Камера（专用于车门联动)
     */
    private void showDoorCamera(String side) {
        // 全景影像避让：目标Activity Передний план时不弹出补盲窗口
        if (isAvmAvoidanceActive) {
            AppLog.d(TAG, "全景影像避让，忽略车门信号: " + side);
            return;
        }

        AppLog.i(TAG, "🚪 ========== showDoorCamera Вкл始выполнение ==========");
        AppLog.i(TAG, "🚪 触发侧: " + side);
        
        // Отмена车门隐藏计时器
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
            hideRunnable = null;
            AppLog.d(TAG, "🚪 Отмена隐藏计时器");
        }
        
        // Отмена信号保活计时器
        if (signalKeepAliveRunnable != null) {
            hideHandler.removeCallbacks(signalKeepAliveRunnable);
            signalKeepAliveRunnable = null;
            AppLog.d(TAG, "🚪 Отмена信号保活计时器");
        }
        
        // --- 1. 尽早创建窗口 UI（addView 触发布局， и 后续 IPC 并行，Surface 绪更快) ---
        boolean reuseMain = appConfig.isTurnSignalReuseMainFloating();
        AppLog.i(TAG, "🚪 复用主屏悬浮窗: " + reuseMain + " (复用转 к 联动конфигурация)");
        
        if (reuseMain) {
            // 复用主屏悬浮窗
            if (mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
                AppLog.d(TAG, "🚪 Закрыто旧 主屏悬浮窗");
            }
            if (WakeUpHelper.hasOverlayPermission(this)) {
                AppLog.i(TAG, "🚪 创建主屏悬浮窗，显示 " + side + " 侧Камера");
                mainFloatingWindowView = new MainFloatingWindowView(this, appConfig);
                mainFloatingWindowView.setDesiredCamera(side, true);
                mainFloatingWindowView.show();
                mainFloatingWindowView.updateStatusLabel(side);
                isMainTempShown = true;
                AppLog.i(TAG, "🚪 ✅ 主屏车门временно补盲悬浮窗显示");
            } else {
                AppLog.e(TAG, "🚪 ❌ 没有Разрешение плавающего окна！");
            }
        } else {
            // использование独立补盲悬浮窗
            if (mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
                isMainTempShown = false;
                AppLog.d(TAG, "🚪 Закрыто主屏悬浮窗");
            }
            if (dedicatedBlindSpotWindow != null) {
                dedicatedBlindSpotWindow.dismiss();
                dedicatedBlindSpotWindow = null;
                AppLog.d(TAG, "🚪 Закрыто旧 独立补盲窗");
            }
            AppLog.i(TAG, "🚪 创建独立补盲窗，显示 " + side + " 侧Камера");
            dedicatedBlindSpotWindow = new BlindSpotFloatingWindowView(this, false);
            dedicatedBlindSpotWindow.setCameraPos(side);
            dedicatedBlindSpotWindow.show();
            dedicatedBlindSpotWindow.updateStatusLabel(side);
            // setCamera необходимо CameraManager，延后 до инициализацияпосле调用
        }
        
        // 副屏窗口预创建（addView 触发布局)
        if (appConfig.isSecondaryDisplayEnabled()) {
            if (secondaryFloatingView == null) {
                AppLog.d(TAG, "🚪 显示副屏");
                showSecondaryDisplay();
            }
        }
        
        // --- 2. 异步ЗапускПередний планСервис и инициализация相机（ и  UI 布局并行) ---
        AppLog.d(TAG, "🚪 ЗапускПередний планСервис");
        CameraForegroundService.start(this, "Слепые зоны активны", "Отображение мониторинга слепых зон");
        AppLog.d(TAG, "🚪 инициализацияКамерауправление器");
        com.kooo.evcam.camera.CameraManagerHolder.getInstance().getOrInit(this);
        
        // --- 3. 提前открыть相机（ и  Surface 创建并行) ---
        {
            MultiCameraManager cm = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
            if (cm != null) {
                SingleCamera cam = cm.getCamera(side);
                if (cam != null && !cam.isCameraOpened()) {
                    CameraForegroundService.whenReady(this, cam::openCameraDeferred);
                }
            }
        }
        
        // --- 4. необходимо CameraManager  операция ---
        if (!reuseMain && dedicatedBlindSpotWindow != null) {
            dedicatedBlindSpotWindow.setCamera(side);
            AppLog.i(TAG, "🚪 ✅ 独立补盲窗显示");
        }
        
        // 副屏Камера预览（复用转 к 联动 конфигурация)
        if (appConfig.isSecondaryDisplayEnabled()) {
            AppLog.d(TAG, "🚪 Запуск副屏Камера预览: " + side);
            startSecondaryCameraPreviewDirectly(side);
        }
        
        AppLog.i(TAG, "🚪 ========== showDoorCamera выполнениезавершение ==========");
    }
    
    /**
     * Запуск车门隐藏计时器（复用转 к 联动 延迟конфигурация)
     */
    private void startDoorHideTimer() {
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
        }
        
        int timeout = appConfig.getTurnSignalTimeout();
        AppLog.i(TAG, "🚪 车门Закрыто，Запуск隐藏计时器: " + timeout + " сек.后ЗакрытоКамера (复用转 к 联动конфигурация)");
        
        hideRunnable = () -> {
            AppLog.i(TAG, "🚪 ⏰ 车门таймаут(" + timeout + " сек.)，隐藏补盲画面");
            
            // Восстановление主屏悬浮窗Статус
            if (isMainTempShown && mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
                isMainTempShown = false;
                AppLog.i(TAG, "🚪 ✅ 主屏车门временно悬浮窗Закрыто");
            } else if (mainFloatingWindowView != null) {
                mainFloatingWindowView.updateCamera(appConfig.getMainFloatingCamera());
            }
            
            // 隐藏独立补盲窗
            if (dedicatedBlindSpotWindow != null) {
                dedicatedBlindSpotWindow.dismiss();
                dedicatedBlindSpotWindow = null;
                AppLog.i(TAG, "🚪 ✅ 独立补盲窗Закрыто");
                
                // Если 原本主屏悬浮窗 Вкл启 ，补盲завершить后необходимоВосстановление它
                if (appConfig.isMainFloatingEnabled()) {
                    updateMainFloatingWindow();
                }
            }
            
            // 副屏显示Восстановление
            updateSecondaryDisplay();
            hideRunnable = null;
            
            // 补盲завершить，Если 没有持久 Surface  用且 Activity  Фоновый режим，释放相机
            closeCamerasIfIdle();
        };
        
        hideHandler.postDelayed(hideRunnable, timeout * 1000L);
    }

    /**
     * 补盲завершить后，проверка 否可以释放相机资源。
     * 条件：Activity  Фоновый режим 且 没有持久悬浮窗/副屏 использование相机。
     */
    private void closeCamerasIfIdle() {
        if (isSelfInForeground) {
            return; // Activity  Передний план，由 Activity управление相机
        }
        if (mainFloatingWindowView != null || secondaryFloatingView != null) {
            return; // 仍有持久 Surface  использование相机
        }
        MultiCameraManager cameraManager = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
        if (cameraManager != null) {
            if (cameraManager.isRecording()) {
                AppLog.d(TAG, "补盲завершить但Выполняется Запись，保持相机Подключение");
                return;
            }
            AppLog.d(TAG, "补盲завершить且无持久 Surface，释放相机资源");
            cameraManager.closeAllCameras();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String mockSignal = intent.getStringExtra("mock_turn_signal");
            if (mockSignal != null) {
                triggerMockSignal(mockSignal);
                return START_STICKY;
            }

            String action = intent.getStringExtra("action");
            if ("setup_blind_spot_window".equals(action)) {
                showBlindSpotSetupWindow();
                return START_STICKY;
            }
            if ("preview_blind_spot".equals(action)) {
                String cameraPos = intent.getStringExtra("camera_pos");
                if (cameraPos == null) cameraPos = "right";
                previewCameraPos = cameraPos;
                showPreviewWindow(cameraPos);
                updateWindows();
                return START_STICKY;
            }
            if ("stop_preview_blind_spot".equals(action)) {
                previewCameraPos = null;
                if (previewBlindSpotWindow != null) {
                    previewBlindSpotWindow.dismiss();
                    previewBlindSpotWindow = null;
                }
                updateWindows();
                return START_STICKY;
            }
            if ("enter_secondary_display_adjust".equals(action)) {
                isSecondaryAdjustMode = true;
                updateWindows();
                return START_STICKY;
            }
            if ("exit_secondary_display_adjust".equals(action)) {
                isSecondaryAdjustMode = false;
                updateWindows();
                return START_STICKY;
            }
        }
        // 重新инициализация新функция（Настройки变更时通过 update() 触发)
        appConfig = new AppConfig(this);
        ensureSignalObserversAlive();
        initAvmAvoidance();
        initCustomKeyWakeup();
        updateWindows();
        return START_STICKY;
    }

    private void showPreviewWindow(String cameraPos) {
        if (!WakeUpHelper.hasOverlayPermission(this)) return;

        if (previewBlindSpotWindow == null) {
            previewBlindSpotWindow = new BlindSpotFloatingWindowView(this, false);
            previewBlindSpotWindow.enableAdjustPreviewMode();
            previewBlindSpotWindow.setCameraPos(cameraPos); // 先НастройкиКамераПозиция，再 show
            previewBlindSpotWindow.show();
        }
        previewBlindSpotWindow.setCamera(cameraPos);

        if (appConfig.isSecondaryDisplayEnabled()) {
            if (secondaryFloatingView == null) {
                showSecondaryDisplay();
            }
            startSecondaryCameraPreviewDirectly(cameraPos);
        }
    }

    private void showBlindSpotSetupWindow() {
        if (dedicatedBlindSpotWindow != null) {
            dedicatedBlindSpotWindow.dismiss();
        }
        dedicatedBlindSpotWindow = new BlindSpotFloatingWindowView(this, true);
        dedicatedBlindSpotWindow.show();
    }

    private void updateWindows() {
        // 全局ВклВыклЗакрыто时，Очистка 所有补盲窗口（调整режим и 预览режим除外)
        if (!appConfig.isBlindSpotGlobalEnabled() && !isSecondaryAdjustMode && previewCameraPos == null) {
            removeSecondaryView();
            if (mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
            }
            if (dedicatedBlindSpotWindow != null) {
                dedicatedBlindSpotWindow.dismiss();
                dedicatedBlindSpotWindow = null;
            }
            removeMockControlWindow();
            currentSignalCamera = null;
            isMainTempShown = false;
            // 定制键唤醒独立于补盲全局ВклВыкл，только当它такжеЗакрыто时才ОстановкаСервис
            if (!appConfig.isCustomKeyWakeupEnabled()) {
                stopSelf();
            }
            return;
        }

        updateSecondaryDisplay();
        updateMainFloatingWindow();
        updateMockControlWindow();
        applyTransforms();
        
        if (isSecondaryAdjustMode
                || appConfig.isMainFloatingEnabled() // 加入主屏悬浮窗проверка
                || appConfig.isTurnSignalLinkageEnabled() // 加入转 к 灯联动проверка
                || appConfig.isDoorLinkageEnabled()  // 加入车门联动проверка
                || appConfig.isMockTurnSignalFloatingEnabled() // 加入模拟转 к 灯проверка
                || appConfig.isAvmAvoidanceEnabled() // 全景影像避让
                || appConfig.isCustomKeyWakeupEnabled() // 定制键唤醒
                || currentSignalCamera != null // 加入转 к 灯联动проверка
                || previewCameraPos != null) {
            CameraForegroundService.start(this, "Слепые зоны активны", "Отображение мониторинга слепых зон");
        }
        
        // Если 两 шт.функциявсеЗакрыто，可以考虑ОстановкаСервис
        // 但若转 к 灯联动или车门联动Вкл启，仍необходимоСервис常驻以便触发补盲窗口
        if (!isSecondaryAdjustMode
                && !appConfig.isMainFloatingEnabled()
                && !appConfig.isTurnSignalLinkageEnabled()
                && !appConfig.isDoorLinkageEnabled()  // 加入车门联动проверка
                && !appConfig.isMockTurnSignalFloatingEnabled()
                && !appConfig.isAvmAvoidanceEnabled() // 全景影像避让
                && !appConfig.isCustomKeyWakeupEnabled() // 定制键唤醒
                && previewCameraPos == null) {
            AppLog.i(TAG, "🚪 所有функциявсеЗакрыто，ОстановкаСервис");
            stopSelf();
        }
    }

    private void applyTransforms() {
        if (mainFloatingWindowView != null) {
            mainFloatingWindowView.applyTransformNow();
        }
        if (dedicatedBlindSpotWindow != null) {
            dedicatedBlindSpotWindow.applyTransformNow();
        }
        if (previewBlindSpotWindow != null) {
            previewBlindSpotWindow.applyTransformNow();
        }
        String secondaryCameraPos = currentSignalCamera != null ? currentSignalCamera : (previewCameraPos != null ? previewCameraPos : secondaryDesiredCameraPos);
        if (secondaryCameraPos != null) {
            BlindSpotCorrection.apply(secondaryTextureView, appConfig, secondaryCameraPos, appConfig.getSecondaryDisplayRotation());
        } else {
            BlindSpotCorrection.apply(secondaryTextureView, appConfig, null, appConfig.getSecondaryDisplayRotation());
        }
    }

    private void triggerMockSignal(String mockSignal) {
        AppLog.d(TAG, "Получена команда: 模拟转 к 灯信号: " + mockSignal);
        handleTurnSignal(mockSignal);

        hideHandler.postDelayed(() -> {
            AppLog.d(TAG, "模拟转 к 灯завершить，выполнение熄灭");
            startHideTimer();
        }, 3000);
    }

    private void updateSecondaryDisplay() {
        boolean shouldShow = isSecondaryAdjustMode || (appConfig.isSecondaryDisplayEnabled() && (currentSignalCamera != null || previewCameraPos != null));

        if (!shouldShow) {
            removeSecondaryView();
            return;
        }

        int desiredDisplayId = appConfig.getSecondaryDisplayId();
        if (secondaryFloatingView != null && secondaryAttachedDisplayId != -1 && secondaryAttachedDisplayId != desiredDisplayId) {
            removeSecondaryView();
        }

        if (secondaryFloatingView == null) {
            showSecondaryDisplay();
        } else {
            updateSecondaryDisplayLayout();
        }

        if (secondaryFloatingView != null) {
            if (isSecondaryAdjustMode) {
                stopSecondaryCameraPreview();
                if (secondaryBorderView != null) {
                    secondaryBorderView.setVisibility(View.VISIBLE);
                }
            } else if (appConfig.isSecondaryDisplayEnabled() && (currentSignalCamera != null || previewCameraPos != null)) {
                if (secondaryBorderView != null) {
                    secondaryBorderView.setVisibility(appConfig.isSecondaryDisplayBorderEnabled() ? View.VISIBLE : View.GONE);
                }
                String cameraPos = currentSignalCamera != null ? currentSignalCamera : previewCameraPos;
                if (cameraPos != null) {
                    startSecondaryCameraPreviewDirectly(cameraPos);
                }
            } else {
                stopSecondaryCameraPreview();
            }
        }
    }

    /**
     * обновление副屏悬浮窗 布局参数 и Поворот 
     */
    private void updateSecondaryDisplayLayout() {
        if (secondaryFloatingView == null || secondaryWindowManager == null) return;

        int x = appConfig.getSecondaryDisplayX();
        int y = appConfig.getSecondaryDisplayY();
        int width = appConfig.getSecondaryDisplayWidth();
        int height = appConfig.getSecondaryDisplayHeight();
        int orientation = appConfig.getSecondaryDisplayOrientation();
        int rotation = appConfig.getSecondaryDisplayRotation();

        AppLog.d(TAG, "обновление副屏布局: x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + ", orientation=" + orientation);

        // Если 方 к   90 или 270 度，交换宽Высокий
        int finalWidth = width;
        int finalHeight = height;
        if (orientation == 90 || orientation == 270) {
            finalWidth = height;
            finalHeight = width;
        }

        WindowManager.LayoutParams params = (WindowManager.LayoutParams) secondaryFloatingView.getLayoutParams();
        params.x = x;
        params.y = y;
        params.width = finalWidth > 0 ? finalWidth : WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = finalHeight > 0 ? finalHeight : WindowManager.LayoutParams.WRAP_CONTENT;

        secondaryWindowManager.updateViewLayout(secondaryFloatingView, params);
        secondaryFloatingView.setRotation(orientation);

        // Приложение透明度
        float alpha = appConfig.getSecondaryDisplayAlpha() / 100f;
        secondaryFloatingView.setAlpha(alpha);

        String cameraPos = currentSignalCamera != null ? currentSignalCamera : (previewCameraPos != null ? previewCameraPos : secondaryDesiredCameraPos);
        BlindSpotCorrection.apply(secondaryTextureView, appConfig, cameraPos, rotation);
        
        // Настройки边框
        if (secondaryBorderView != null) {
            if (isSecondaryAdjustMode) {
                secondaryBorderView.setVisibility(View.VISIBLE);
            } else {
                secondaryBorderView.setVisibility(appConfig.isSecondaryDisplayBorderEnabled() ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void showSecondaryDisplay() {
        if (secondaryFloatingView != null) return; // 经显示

        int displayId = appConfig.getSecondaryDisplayId();
        Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            AppLog.e(TAG, "找不 до 指定 副屏 Display ID: " + displayId);
            return;
        }
        secondaryAttachedDisplayId = displayId;

        // 创建 应显示器  Context
        Context displayContext;
        try {
            displayContext = createDisplayContext(display);
        } catch (Exception e) {
            AppLog.e(TAG, "创建副屏 Context Ошибка（APK 资源可能不Доступно): " + e.getMessage());
            return;
        }
        if (displayContext.getResources() == null) {
            AppLog.e(TAG, "副屏 Context 资源пусто，跳过显示");
            return;
        }
        secondaryWindowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);

        // загрузка布局
        secondaryFloatingView = LayoutInflater.from(displayContext).inflate(R.layout.presentation_secondary_display, null);
        secondaryTextureView = secondaryFloatingView.findViewById(R.id.secondary_texture_view);
        secondaryBorderView = secondaryFloatingView.findViewById(R.id.secondary_border);

        // Настройки边框
        secondaryBorderView.setVisibility(isSecondaryAdjustMode ? View.VISIBLE :
                (appConfig.isSecondaryDisplayBorderEnabled() ? View.VISIBLE : View.GONE));

        // Настройки悬浮窗参数
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int x = appConfig.getSecondaryDisplayX();
        int y = appConfig.getSecondaryDisplayY();
        int width = appConfig.getSecondaryDisplayWidth();
        int height = appConfig.getSecondaryDisplayHeight();
        int orientation = appConfig.getSecondaryDisplayOrientation();
        int rotation = appConfig.getSecondaryDisplayRotation();

        AppLog.d(TAG, "显示副屏: x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + ", orientation=" + orientation + ", rotation=" + rotation);

        // Если 方 к   90 или 270 度，交换宽Высокий
        int finalWidth = width;
        int finalHeight = height;
        if (orientation == 90 || orientation == 270) {
            finalWidth = height;
            finalHeight = width;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                finalWidth > 0 ? finalWidth : WindowManager.LayoutParams.WRAP_CONTENT,
                finalHeight > 0 ? finalHeight : WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;

        // Настройки屏幕方 к  (Поворот 整 шт.容器)
        // 注意：某些车机Система  WindowManager 根视图  setRotation поддержка有限
        // 我们попытка同时НастройкиПоворот  и Внутреннее视图 变换
        secondaryFloatingView.setRotation(orientation);

        // Настройки内容Поворот  (将 orientation  и  rotation 结合处理)
        // 最终Поворот 角度 = Камера内容Поворот  + 屏幕方 к 补偿
        String cameraPos = currentSignalCamera != null ? currentSignalCamera : (previewCameraPos != null ? previewCameraPos : secondaryDesiredCameraPos);
        BlindSpotCorrection.apply(secondaryTextureView, appConfig, cameraPos, rotation);

        secondaryTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int w, int h) {
                String cameraPos = null;
                if (appConfig.isSecondaryDisplayEnabled()) {
                    if (secondaryDesiredCameraPos != null) {
                        cameraPos = secondaryDesiredCameraPos;
                    } else if (previewCameraPos != null) {
                        cameraPos = previewCameraPos;
                    } else if (currentSignalCamera != null) {
                        cameraPos = currentSignalCamera;
                    }
                }
                if (cameraPos == null) {
                    AppLog.d(TAG, "副屏 Surface 绪，但Не ВключитьВидео输出");
                    return;
                }
                AppLog.d(TAG, "副屏 Surface 绪，Запуск预览: " + cameraPos);
                startSecondaryCameraPreviewDirectly(cameraPos);
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int w, int h) {}

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
                // СохранитьТекущий TextureView  引用，用于判断回调 否来自旧 替换  TextureView
                final TextureView currentTv = secondaryTextureView;
                if (currentTv != null) {
                    android.graphics.SurfaceTexture currentSt = currentTv.getSurfaceTexture();
                    // Если Текущий副屏  SurfaceTexture 不  销毁 那 шт.，说明 旧  TextureView
                    if (currentSt != null && currentSt != surface) {
                        AppLog.d(TAG, "Ignoring old secondary TextureView destroy callback");
                        return true;
                    }
                }
                stopSecondaryCameraPreview();
                if (secondaryCachedSurface != null) {
                    secondaryCachedSurface.release();
                    secondaryCachedSurface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) {}
        });

        // Приложение透明度
        float alpha = appConfig.getSecondaryDisplayAlpha() / 100f;
        secondaryFloatingView.setAlpha(alpha);

        try {
            secondaryWindowManager.addView(secondaryFloatingView, params);
        } catch (Exception e) {
            AppLog.e(TAG, "无法添加副屏悬浮窗: " + e.getMessage());
        }
    }

    private void updateMainFloatingWindow() {
        // 全景影像避让：目标Activity Передний план时不显示主屏补盲窗口
        if (isAvmAvoidanceActive) {
            AppLog.d(TAG, "全景影像避让，跳过主屏悬浮窗обновление");
            return;
        }

        if (appConfig.isMainFloatingEnabled()) {
            isMainTempShown = false; // 用户Вкл启
            if (mainFloatingWindowView == null) {
                if (WakeUpHelper.hasOverlayPermission(this)) {
                    mainFloatingWindowView = new MainFloatingWindowView(this, appConfig);
                    mainFloatingWindowView.show();
                }
            } else {
                mainFloatingWindowView.updateLayout();
            }
            if (mainFloatingWindowView != null && currentSignalCamera == null) {
                mainFloatingWindowView.updateCamera(appConfig.getMainFloatingCamera());
            }
        } else if (currentSignalCamera == null) {
            if (mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
            }
            isMainTempShown = false;
        }
    }

    private void stopSecondaryCameraPreview() {
        if (secondaryCamera != null) {
            // 立т.е.Остановка推帧并Закрыто session，确保 Surface  释放
            // 这样新Камера才能использование同一 шт. Surface，避免 "connect: already connected"
            secondaryCamera.stopRepeatingNow();
            secondaryCamera.setSecondaryDisplaySurface(null);
            secondaryCamera.recreateSession();
            secondaryCamera = null;
        }
    }

    private void removeSecondaryView() {
        stopSecondaryCameraPreview();
        secondaryDesiredCameraPos = null;
        secondaryAttachedDisplayId = -1;
        if (secondaryWindowManager != null && secondaryFloatingView != null) {
            try {
                secondaryWindowManager.removeView(secondaryFloatingView);
            } catch (Exception e) {
                // Ignore
            }
            secondaryFloatingView = null;
            secondaryTextureView = null;
            secondaryBorderView = null;
            secondaryWindowManager = null;
        }
        if (secondaryCachedSurface != null) {
            secondaryCachedSurface.release();
            secondaryCachedSurface = null;
        }
    }

    private void updateMockControlWindow() {
        if (appConfig.isMockTurnSignalFloatingEnabled()) {
            showMockControlWindow();
        } else {
            removeMockControlWindow();
        }
    }

    private void showMockControlWindow() {
        if (mockControlView != null) return;
        if (!WakeUpHelper.hasOverlayPermission(this)) {
            appConfig.setMockTurnSignalFloatingEnabled(false);
            return;
        }

        mockControlWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (mockControlWindowManager == null) {
            appConfig.setMockTurnSignalFloatingEnabled(false);
            return;
        }

        mockControlView = LayoutInflater.from(this).inflate(R.layout.view_mock_turn_signal_floating, null);
        Button leftButton = mockControlView.findViewById(R.id.btn_mock_left);
        Button rightButton = mockControlView.findViewById(R.id.btn_mock_right);
        Button closeButton = mockControlView.findViewById(R.id.btn_close);
        TextView dragHandle = mockControlView.findViewById(R.id.tv_drag_handle);

        leftButton.setOnClickListener(v -> triggerMockSignal("left"));
        rightButton.setOnClickListener(v -> triggerMockSignal("right"));
        closeButton.setOnClickListener(v -> {
            appConfig.setMockTurnSignalFloatingEnabled(false);
            updateWindows();
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int x = appConfig.getMockTurnSignalFloatingX();
        int y = appConfig.getMockTurnSignalFloatingY();

        mockControlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        mockControlParams.gravity = Gravity.TOP | Gravity.START;
        mockControlParams.x = x;
        mockControlParams.y = y;

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mockControlParams == null || mockControlWindowManager == null || mockControlView == null) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mockControlParams.x;
                        initialY = mockControlParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mockControlParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mockControlParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            mockControlWindowManager.updateViewLayout(mockControlView, mockControlParams);
                        } catch (Exception e) {
                            AppLog.e(TAG, "обновление模拟悬浮窗ПозицияОшибка: " + e.getMessage());
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        appConfig.setMockTurnSignalFloatingPosition(mockControlParams.x, mockControlParams.y);
                        return true;
                }
                return false;
            }
        });

        try {
            mockControlWindowManager.addView(mockControlView, mockControlParams);
        } catch (Exception e) {
            AppLog.e(TAG, "无法添加模拟悬浮窗: " + e.getMessage());
            mockControlView = null;
            mockControlWindowManager = null;
            mockControlParams = null;
            appConfig.setMockTurnSignalFloatingEnabled(false);
        }
    }

    private void removeMockControlWindow() {
        if (mockControlWindowManager != null && mockControlView != null) {
            try {
                mockControlWindowManager.removeView(mockControlView);
            } catch (Exception e) {
                // Ignore
            }
        }
        mockControlView = null;
        mockControlWindowManager = null;
        mockControlParams = null;
    }

    // ==================== 全景影像避让 ====================

    /**
     * инициализация全景影像避让（Передний планActivity检测轮询)
     */
    private void initAvmAvoidance() {
        stopAvmAvoidance();
        if (!appConfig.isAvmAvoidanceEnabled()) return;

        String target = appConfig.getAvmAvoidanceActivity();
        AppLog.d(TAG, "Запуск全景影像避让检测，目标Activity: " + target);

        // "all" режим：始终避让，不необходимо轮询检测Передний планПриложение
        if ("all".equalsIgnoreCase(target)) {
            isAvmAvoidanceActive = true;
            AppLog.i(TAG, "全景影像避让：all режим，主屏补盲窗口始终隐藏");
            if (mainFloatingWindowView != null) {
                mainFloatingWindowView.dismiss();
                mainFloatingWindowView = null;
            }
            if (dedicatedBlindSpotWindow != null) {
                dedicatedBlindSpotWindow.dismiss();
                dedicatedBlindSpotWindow = null;
            }
            return;
        }

        avmCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!appConfig.isAvmAvoidanceEnabled()) {
                    stopAvmAvoidance();
                    return;
                }
                checkAvmForeground();
                hideHandler.postDelayed(this, AVM_CHECK_INTERVAL_MS);
            }
        };
        hideHandler.post(avmCheckRunnable);
    }

    /**
     * Остановка全景影像避让检测
     */
    private void stopAvmAvoidance() {
        if (avmCheckRunnable != null) {
            hideHandler.removeCallbacks(avmCheckRunnable);
            avmCheckRunnable = null;
        }
        if (isAvmAvoidanceActive) {
            isAvmAvoidanceActive = false;
            // Восстановление窗口显示
            updateMainFloatingWindow();
        }
    }

    /**
     * 检测目标Activity 否 Передний план，并相应隐藏/Восстановление主屏补盲窗口
     */
    private void checkAvmForeground() {
        String targetActivity = appConfig.getAvmAvoidanceActivity();
        if (targetActivity == null || targetActivity.isEmpty()) return;

        // "all" режим始终视为Передний план，主屏补盲永不显示
        boolean isAvmForeground = "all".equalsIgnoreCase(targetActivity)
                || isActivityInForeground(targetActivity);

        // EVCam 自身Передний план检测（基于 Activity 生命周期，т.е.时准确，不依赖 UsageEvents)
        boolean selfFg = isSelfInForeground;

        if (isAvmForeground || selfFg) {
            if (isAvmForeground) {
                avmDeactivateCount = 0; // AVM 确实 Передний план，Сброс去抖
            }
            if (!isAvmAvoidanceActive) {
                isAvmAvoidanceActive = true;
                AppLog.i(TAG, "全景影像避让：隐藏主屏补盲窗口（AVM=" + isAvmForeground + ", 自身Передний план=" + selfFg + ")");
                if (mainFloatingWindowView != null) {
                    mainFloatingWindowView.dismiss();
                    mainFloatingWindowView = null;
                }
                if (dedicatedBlindSpotWindow != null) {
                    dedicatedBlindSpotWindow.dismiss();
                    dedicatedBlindSpotWindow = null;
                }
            }
        } else if (isAvmAvoidanceActive) {
            // 两 шт.条件все不满足：AVM 不 Передний план，EVCam также不 Передний план
            avmDeactivateCount++;
            AppLog.d(TAG, "全景影像避让：Не ОбнаруженоПередний план (" + avmDeactivateCount + "/" + AVM_DEACTIVATE_THRESHOLD + ")");
            if (avmDeactivateCount >= AVM_DEACTIVATE_THRESHOLD) {
                isAvmAvoidanceActive = false;
                avmDeactivateCount = 0;
                AppLog.i(TAG, "全景影像避让：" + targetActivity + " 离ВклПередний план，Восстановление主屏补盲窗口");
                updateMainFloatingWindow();
            }
        }
    }

    /**
     * 检测指定Activity（完整类名) 否 Передний план
     * использование UsageEvents 精确 до  Activity 级别（需 PACKAGE_USAGE_STATS Разрешение)
     * 查询最近5 мин. 事件，追踪最后一 разПередний план/Фоновый режим切换来判断ТекущийСтатус
     */
    private boolean isActivityInForeground(String activityClassName) {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return false;

            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents events = usm.queryEvents(now - 300000, now);
            if (events == null) return false;

            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            Boolean targetLastState = null;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                String className = event.getClassName();
                if (activityClassName.equals(className)) {
                    if (event.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        targetLastState = true;
                    } else if (event.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        targetLastState = false;
                    }
                }
            }

            return targetLastState != null && targetLastState;
        } catch (Exception e) {
            AppLog.e(TAG, "检测Передний планActivityОшибка: " + e.getMessage());
        }
        return false;
    }

    /**
     * 检测指定包名 Приложение 否 Передний план
     * использование UsageEvents 查询最近5 мин. 事件，追踪该包名任意Activity 最后Передний план/Фоновый режимСтатус
     */
    private boolean isPackageInForeground(String packageName) {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return false;

            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents events = usm.queryEvents(now - 300000, now);
            if (events == null) return false;

            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            Boolean lastState = null;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (packageName.equals(event.getPackageName())) {
                    if (event.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastState = true;
                    } else if (event.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        lastState = false;
                    }
                }
            }

            return lastState != null && lastState;
        } catch (Exception e) {
            AppLog.e(TAG, "检测Передний план包名Ошибка: " + e.getMessage());
        }
        return false;
    }

    // ==================== 定制键唤醒 ====================

    /**
     * инициализация定制键唤醒（конфигурация信号观察者  CustomKeyListener)
     */
    private void initCustomKeyWakeup() {
        if (!appConfig.isCustomKeyWakeupEnabled()) return;

        AppLog.d(TAG, "Запуск定制键唤醒，速度属性=" + appConfig.getCustomKeySpeedPropId()
                + ", св-во кнопки=" + appConfig.getCustomKeyButtonPropId()
                + ", порог скорости=" + appConfig.getCustomKeySpeedThreshold());

        // Если 信号观察者还Не 创建，先创建一 шт.
        if (vhalSignalObserver == null) {
            vhalSignalObserver = new VhalSignalObserver(new VhalSignalObserver.TurnSignalListener() {
                @Override
                public void onTurnSignal(String direction, boolean on) {
                    // 转 к 联动Не Включить，忽略
                }
                @Override
                public void onConnectionStateChanged(boolean connected) {
                    AppLog.d(TAG, "Vehicle API connection (custom key): " + (connected ? "connected" : "disconnected"));
                }
            });
            vhalSignalObserver.start();
        }

        vhalSignalObserver.configureCustomKey(
                appConfig.getCustomKeySpeedPropId(),
                appConfig.getCustomKeyButtonPropId(),
                appConfig.getCustomKeySpeedThreshold()
        );

        vhalSignalObserver.setCustomKeyListener(() -> {
            AppLog.d(TAG, "定制键唤醒：按钮触发");
            toggleCustomKeyPreview();
        });
    }

    /**
     * 切换定制键唤醒 预览Статус
     */
    private void toggleCustomKeyPreview() {
        if (isCustomKeyPreviewShown) {
            // Текущий显示，Выход до Фоновый режим
            AppLog.d(TAG, "定制键唤醒：Выход预览 до Фоновый режим");
            isCustomKeyPreviewShown = false;
            WakeUpHelper.sendBackgroundBroadcast(this);
        } else {
            // проверка速度条件
            float speedThreshold = appConfig.getCustomKeySpeedThreshold();
            if (vhalSignalObserver != null && vhalSignalObserver.getCurrentSpeed() < speedThreshold) {
                AppLog.d(TAG, "定制键唤醒：速度Не 达 до 阈值，忽略");
                return;
            }
            // 唤醒预览界面
            AppLog.d(TAG, "定制键唤醒：唤醒预览界面");
            isCustomKeyPreviewShown = true;
            WakeUpHelper.launchForForeground(this);
        }
    }

    @Override
    public void onDestroy() {
        stopSignalObservers();
        stopAvmAvoidance();
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
        }
        if (signalKeepAliveRunnable != null) {
            hideHandler.removeCallbacks(signalKeepAliveRunnable);
        }
        cancelSecondaryRetry();
        removeSecondaryView();
        removeMockControlWindow();
        if (mainFloatingWindowView != null) {
            mainFloatingWindowView.dismiss();
        }
        if (dedicatedBlindSpotWindow != null) {
            dedicatedBlindSpotWindow.dismiss();
        }
        if (previewBlindSpotWindow != null) {
            previewBlindSpotWindow.dismiss();
        }
        sInstance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * обновлениеСервисСтатус
     */
    public static void update(Context context) {
        Intent intent = new Intent(context, BlindSpotService.class);
        context.startService(intent);
    }
}
