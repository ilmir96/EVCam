package com.kooo.evcam;


import com.kooo.evcam.AppLog;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;
import com.kooo.evcam.camera.ImageAdjustManager;
import com.kooo.evcam.camera.MultiCameraManager;
import com.kooo.evcam.camera.SingleCamera;
import com.kooo.evcam.FileTransferManager;
import com.kooo.evcam.StorageHelper;
import com.kooo.evcam.dingtalk.DingTalkApiClient;
import com.kooo.evcam.dingtalk.DingTalkConfig;
import com.kooo.evcam.dingtalk.DingTalkStreamManager;
import com.kooo.evcam.telegram.TelegramApiClient;
import com.kooo.evcam.telegram.TelegramBotManager;
import com.kooo.evcam.telegram.TelegramConfig;
import com.kooo.evcam.remote.RemoteCommandDispatcher;
import com.kooo.evcam.remote.handler.RemoteCommandHandler;
import com.kooo.evcam.playback.PlaybackFragmentNew;
import com.kooo.evcam.playback.PhotoPlaybackFragmentNew;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;
    
    // 静态实例引用（用于悬浮窗等Внешнее групп件доступ)
    private static MainActivity instance;

    // 根据Android版本动态Получениенеобходимо Разрешение
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            };
        } else {
            // Android 12及и ниже
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
    }

    private AutoFitTextureView textureFront, textureBack, textureLeft, textureRight;
    private final java.util.Map<String, android.graphics.Matrix> previewBaseTransforms = new java.util.HashMap<>();
    private PreviewCorrectionFloatingWindow previewCorrectionFloatingWindow;
    private FisheyeCorrectionFloatingWindow fisheyeCorrectionFloatingWindow;

    // отладкаИнформация覆盖层（连点5空白处显示)
    private TextView tvDebugOverlay;
    private boolean debugOverlayVisible = false;
    private final android.os.Handler debugUpdateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable debugUpdateRunnable;
    private int debugTapCount = 0;
    private long debugLastTapTime = 0;
    private static final int DEBUG_TAP_COUNT = 5;
    private static final long DEBUG_TAP_INTERVAL_MS = 800;  // 连续点击 максимум间隔

    private Button btnStartRecord, btnExit, btnTakePhoto;
    private MultiCameraManager cameraManager;

    public MultiCameraManager getCameraManager() {
        if (cameraManager == null) {
            cameraManager = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
        }
        return cameraManager;
    }
    private ImageAdjustManager imageAdjustManager;  // 亮度/Шумоподавление调节управление器
    private ImageAdjustFloatingWindow imageAdjustFloatingWindow;  // 亮度/Шумоподавление调节悬浮窗
    private int textureReadyCount = 0;  // 记录准备好 TextureView数量
    private int requiredTextureCount = 4;  // необходимо准备好 TextureView数量（根据Камера数量)
    private boolean isRecording = false;  // ЗаписьСтатус标志
    private boolean isInBackground = false;  //  否 Фоновый режим
    private boolean pendingRemoteCommand = false;  //  否有待处理 Удалённыйкоманда
    private boolean isRemoteWakeUp = false;  //  否 Удалённыйкоманда唤醒 （用于завершение后автоматически退回Фоновый режим)
    private boolean hasBeenResumedOnce = false;  // Activity  否经完全Восстановление过一 раз（用于区分新创建 и существует)
    
    // 防双击保护
    private long lastRecordButtonClickTime = 0;  //  раз点击Запись按钮 时间
    private static final long RECORD_BUTTON_CLICK_INTERVAL = 1000;  // минимум点击间隔（1 сек.)
    
    // ЗаписьаномалияУведомление防抖
    private long lastRecordingErrorToastTime = 0;  //  раз显示ЗаписьаномалияУведомление 时间
    private static final long RECORDING_ERROR_TOAST_INTERVAL = 20000;  // минимум显示间隔（20 сек.)
    private boolean shouldMoveToBackgroundOnReady = false;  // Вкл机自Запуск后，窗口准备好时移 до Фоновый режим
    private boolean autoStartRecordingTriggered = false;  // 标记автоматическиЗапись 否触发（避免重复触发)
    private boolean isAutoRecordingPending = false;  // 标记автоматическиЗапись计划但尚Не Вкл始（防止 onPause ЗакрытоКамера)
    
    // автоматическиЗаписьПлановая проверка相Выкл
    private boolean isManuallyStoppedRecording = false;  // 用户 否вручнуюОстановкаЗапись（вручнуюОстановка后不автоматическиВосстановление)
    private android.os.Handler autoRecordingCheckHandler;  // Плановая проверка Handler
    private Runnable autoRecordingCheckRunnable;  // Плановая проверка Runnable
    private static final long AUTO_RECORDING_CHECK_INTERVAL_MS = 30000;  // проверка间隔（30 сек.)
    
    // 主题切换后ВосстановлениеЗапись相Выкл
    private boolean shouldResumeRecordingAfterRecreate = false;  // 主题切换后 否необходимоВосстановлениеЗапись
    private long savedRecordingStartTime = 0;  // Сохранить ЗаписьВкл始时间（用于计时器Восстановление)
    private int savedSegmentCount = 1;  // Сохранить 分数
    
    // Камера重连防抖相Выкл
    private android.os.Handler reopenCameraHandler;  // 重新открытьКамера  Handler
    private Runnable reopenCameraRunnable;  // 重新открытьКамера  Runnable
    
    // 息屏Запись相Выкл
    private android.content.BroadcastReceiver screenStateReceiver;  // 屏幕Статус广播接收器
    private android.content.BroadcastReceiver backgroundCommandReceiver;  // Фоновый режим切换广播接收器
    private android.os.Handler screenStateHandler;  // 息屏/亮屏延迟处理
    private Runnable screenOffStopRunnable;  // 息屏Остановить запись 延迟задача
    private Runnable screenOnStartRunnable;  // 亮屏ВосстановлениеЗапись 延迟задача
    private Runnable screenOffBackgroundRunnable;  // 息屏退Фоновый режим 延迟задача
    private boolean isScreenOff = false;  // Текущий 否息屏
    private boolean wasRecordingBeforeScreenOff = false;  // 息屏前 否Выполняется Запись
    private static final long SCREEN_OFF_DELAY_MS = 10000;  // 息屏后ожидание10 сек.（Остановить запись)
    private static final long SCREEN_ON_DELAY_MS = 10000;   // 亮屏后ожидание10 сек.（ВосстановлениеЗапись)
    private static final long SCREEN_OFF_BACKGROUND_DELAY_MS = 15000;  // 息屏后ожидание15 сек.（退Фоновый режим)
    
    
    // 车型конфигурация相Выкл
    private AppConfig appConfig;
    private int configuredCameraCount = 4;  // конфигурация Камера数量
    private CustomLayoutManager customLayoutManager;  // Своя модель布局управление器

    // Запись按钮闪烁动画相Выкл
    private android.os.Handler blinkHandler;
    private Runnable blinkRunnable;
    private boolean isBlinking = false;

    // ЗаписьСтатус显示相Выкл
    private TextView tvRecordingStats;
    private android.os.Handler recordingTimerHandler;

    private Runnable recordingTimerRunnable;
    private long recordingStartTime = 0;  // ЗаписьВкл始时间
    private int currentSegmentCount = 1;  // Текущий分数
    private boolean isRecordingStatsEnabled = true;  // ЗаписьСтатус显示ВклВыкл
    private long lastStatsClickTime = 0;  //  раз点击ЗаписьСтатус显示 时间
    private static final long DOUBLE_CLICK_INTERVAL = 500;  // 双击判定间隔（毫 сек.)

    // 导航相Выкл
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private View recordingLayout;  // Запись界面布局
    private View fragmentContainer;  // Fragment容器


    // Удалённая запись相Выкл
    private android.os.Handler autoStopHandler;  // автоматическиОстановить запись  Handler
    private Runnable autoStopRunnable;  // автоматическиОстановить запись  Runnable
    private String remoteRecordingTimestamp;  // Удалённая запись统一时间戳（用于Файл命名 и 查找)
    private boolean isRemoteRecording = false;  //  否Выполняется 进行Удалённая запись
    private boolean wasManualRecordingBeforeRemote = false;  // Удалённая запись前 否有вручнуюЗапись 进行
    private int pendingRemoteDurationSeconds = 0;  // 待Запуск Удалённая запись时长（ожидание首 раз写入后Запуск定时器)
    private boolean isPreparingRecording = false;  //  否Выполняется 准备Запись（ожидание首 раз写入)

    // УдалённыйПросмотрСервис相Выкл（移 до  Activity 级别)
    private DingTalkConfig dingTalkConfig;
    private DingTalkApiClient dingTalkApiClient;
    private DingTalkStreamManager dingTalkStreamManager;
    
    // Telegram УдалённыйСервис相Выкл
    private TelegramConfig telegramConfig;
    private TelegramApiClient telegramApiClient;
    private TelegramBotManager telegramBotManager;
    private long pendingTelegramChatId = 0;  // 待处理  Telegram Chat ID

    // FeishuУдалённыйСервис相Выкл
    private com.kooo.evcam.feishu.FeishuConfig feishuConfig;
    private com.kooo.evcam.feishu.FeishuApiClient feishuApiClient;
    private com.kooo.evcam.feishu.FeishuBotManager feishuBotManager;
    private String pendingFeishuChatId = null;  // 待处理 Feishu Chat ID
    
    // СтатусИнформация提供者（必须保持强引用，否则会  GC 回收导致УдалённыйСтатус查询Ошибка)
    private RemoteServiceManager.StatusInfoProvider statusInfoProvider;
    
    // ХранилищеОчистка управление器
    private StorageCleanupManager storageCleanupManager;
    
    // Удалённыйкоманда分发器（重构后 统一入口)
    private RemoteCommandDispatcher remoteCommandDispatcher;
    
    // Мониторингуправление器
    private com.kooo.evcam.heartbeat.HeartbeatManager heartbeatManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;  // Настройки静态实例引用
        AppLog.init(this);

        // Настройки字体缩放比例（1.3倍)
        adjustFontScale(1.2f);

        // инициализацияПриложениеконфигурация
        appConfig = new AppConfig(this);
        
        // СбросUSB-накопитель回退Уведомление标志（每 раз冷ЗапускСброс)
        AppConfig.resetSdFallbackFlag();
        
        // 根据车型конфигурацияНастройки布局 и Камера数量
        setupLayoutByCarModel();

        // НастройкиСтатус栏沉浸式
        setupStatusBar();

        initViews();
        setupNavigationDrawer();

        // проверка 否необходимо 主题切换后ВосстановлениеЗапись
        if (savedInstanceState != null) {
            boolean wasRecording = savedInstanceState.getBoolean("wasRecording", false);
            if (wasRecording) {
                shouldResumeRecordingAfterRecreate = true;
                savedRecordingStartTime = savedInstanceState.getLong("recordingStartTime", 0);
                savedSegmentCount = savedInstanceState.getInt("segmentCount", 1);
                AppLog.d(TAG, "onCreate: Обнаружено主题切换，необходимоВосстановлениеЗапись - savedStartTime=" + savedRecordingStartTime + ", savedSegment=" + savedSegmentCount);
            }
        }

        // проверка 否首 разЗапуск
        checkFirstLaunch();

        // инициализацияDingTalkконфигурация
        dingTalkConfig = new DingTalkConfig(this);
        
        // инициализация Telegram конфигурация
        telegramConfig = new TelegramConfig(this);

        // инициализацияFeishuконфигурация
        feishuConfig = new com.kooo.evcam.feishu.FeishuConfig(this);

        // инициализацияавтоматическиОстановка Handler
        autoStopHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        // инициализацияУдалённая запись时间戳
        remoteRecordingTimestamp = null;
        
        // инициализацияУдалённыйкоманда分发器
        initRemoteCommandDispatcher();

        // Разрешениепроверка，但不立т.е.инициализацияКамера
        // ожиданиеTextureView准备好后再инициализация
        if (!checkPermissions()) {
            requestPermissions();
        }

        // Если ВключитьавтоматическиЗапуск，ЗапускУдалённыйПросмотрСервис
        // 【优化】先проверкаСервис 否  CameraForegroundService ЗапускилиВыполняется Запуск
        if (dingTalkConfig.isConfigured() && dingTalkConfig.isAutoStart()) {
            if (RemoteServiceManager.getInstance().isDingTalkStartingOrRunning()) {
                AppLog.d(TAG, "DingTalkСервис РаботаилиВыполняется Запуск（ от  Service Запуск)，Получение有实例");
                // 【重要】 от  RemoteServiceManager Получение有  API 客户端，用于Файл传
                // 注意：Если Выполняется Запуск，这些可能暂时为 null，但СервисЗапускзавершение后会 Настройки
                dingTalkApiClient = RemoteServiceManager.getInstance().getDingTalkApiClient();
                dingTalkStreamManager = RemoteServiceManager.getInstance().getDingTalkStreamManager();
                
                // 【修复】立т.е.同步 до  RemoteCommandDispatcher（Если инициализация且ПолучениеУспешно)
                if (dingTalkApiClient != null && remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.setDingTalkApiClient(dingTalkApiClient);
                    AppLog.d(TAG, "DingTalk API 客户端同步 до  RemoteCommandDispatcher");
                }
                
                // Если СервисВыполняется Запуск，延迟Получение实例
                if (dingTalkApiClient == null || dingTalkStreamManager == null) {
                    AppLog.d(TAG, "DingTalkСервисВыполняется Запуск，延迟 500ms 后Получение实例");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        dingTalkApiClient = RemoteServiceManager.getInstance().getDingTalkApiClient();
                        dingTalkStreamManager = RemoteServiceManager.getInstance().getDingTalkStreamManager();
                        AppLog.d(TAG, "延迟ПолучениеDingTalk实例: apiClient=" + (dingTalkApiClient != null) + 
                                     ", streamManager=" + (dingTalkStreamManager != null));
                        // 【修复】延迟Получение后такженеобходимо同步 до  RemoteCommandDispatcher
                        if (dingTalkApiClient != null && remoteCommandDispatcher != null) {
                            remoteCommandDispatcher.setDingTalkApiClient(dingTalkApiClient);
                            AppLog.d(TAG, "DingTalk API 客户端延迟同步 до  RemoteCommandDispatcher");
                        }
                    }, 500);
                }
            } else {
                startDingTalkService();
            }
        }
        
        // Если Включить Telegram автоматическиЗапуск，Запуск Telegram Сервис
        if (telegramConfig.isConfigured() && telegramConfig.isAutoStart()) {
            if (RemoteServiceManager.getInstance().isTelegramStartingOrRunning()) {
                AppLog.d(TAG, "Telegram Сервис РаботаилиВыполняется Запуск（ от  Service Запуск)，Получение有实例");
                // 【重要】 от  RemoteServiceManager Получение有  API 客户端，用于Файл传
                telegramApiClient = RemoteServiceManager.getInstance().getTelegramApiClient();
                telegramBotManager = RemoteServiceManager.getInstance().getTelegramBotManager();
                
                // 【修复】立т.е.同步 до  RemoteCommandDispatcher
                if (telegramApiClient != null && remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.setTelegramApiClient(telegramApiClient);
                    AppLog.d(TAG, "Telegram API 客户端同步 до  RemoteCommandDispatcher");
                }
                
                // Если СервисВыполняется Запуск，延迟Получение实例
                if (telegramApiClient == null || telegramBotManager == null) {
                    AppLog.d(TAG, "Telegram СервисВыполняется Запуск，延迟 500ms 后Получение实例");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        telegramApiClient = RemoteServiceManager.getInstance().getTelegramApiClient();
                        telegramBotManager = RemoteServiceManager.getInstance().getTelegramBotManager();
                        AppLog.d(TAG, "延迟Получение Telegram 实例: apiClient=" + (telegramApiClient != null) + 
                                     ", botManager=" + (telegramBotManager != null));
                        // 【修复】延迟Получение后такженеобходимо同步
                        if (telegramApiClient != null && remoteCommandDispatcher != null) {
                            remoteCommandDispatcher.setTelegramApiClient(telegramApiClient);
                            AppLog.d(TAG, "Telegram API 客户端延迟同步 до  RemoteCommandDispatcher");
                        }
                    }, 500);
                }
            } else {
                startTelegramService();
            }
        }

        // Если ВключитьFeishuавтоматическиЗапуск，ЗапускFeishuСервис
        if (feishuConfig.isConfigured() && feishuConfig.isAutoStart()) {
            if (RemoteServiceManager.getInstance().isFeishuStartingOrRunning()) {
                AppLog.d(TAG, "FeishuСервис РаботаилиВыполняется Запуск（ от  Service Запуск)，Получение有实例");
                feishuApiClient = RemoteServiceManager.getInstance().getFeishuApiClient();
                feishuBotManager = RemoteServiceManager.getInstance().getFeishuBotManager();
                
                // 【修复】立т.е.同步 до  RemoteCommandDispatcher
                if (feishuApiClient != null && remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.setFeishuApiClient(feishuApiClient);
                    AppLog.d(TAG, "Feishu API 客户端同步 до  RemoteCommandDispatcher");
                }
                
                if (feishuApiClient == null || feishuBotManager == null) {
                    AppLog.d(TAG, "FeishuСервисВыполняется Запуск，延迟 500ms 后Получение实例");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        feishuApiClient = RemoteServiceManager.getInstance().getFeishuApiClient();
                        feishuBotManager = RemoteServiceManager.getInstance().getFeishuBotManager();
                        AppLog.d(TAG, "延迟ПолучениеFeishu实例: apiClient=" + (feishuApiClient != null) + 
                                     ", botManager=" + (feishuBotManager != null));
                        // 【修复】延迟Получение后такженеобходимо同步
                        if (feishuApiClient != null && remoteCommandDispatcher != null) {
                            remoteCommandDispatcher.setFeishuApiClient(feishuApiClient);
                            AppLog.d(TAG, "Feishu API 客户端延迟同步 до  RemoteCommandDispatcher");
                        }
                    }, 500);
                }
            } else {
                startFeishuService();
            }
        }
        
        // 注册СтатусИнформация提供者，让УдалённыйСервис能Получение完整 СтатусИнформация
        // 注意：必须保持强引用（statusInfoProvider 成员变量)，否则 WeakReference 会导致 象  GC
        statusInfoProvider = new RemoteServiceManager.StatusInfoProvider() {
            @Override
            public String getFullStatusInfo() {
                // 直接использованиеВнешнее类引用，因为 statusInfoProvider  生命周期 и  MainActivity 绑定
                if (!isDestroyed()) {
                    return buildStatusInfo();
                }
                return null; // Возвращает null 会触发использование基本СтатусИнформация
            }
        };
        RemoteServiceManager.getInstance().setStatusInfoProvider(statusInfoProvider);
        AppLog.d(TAG, "StatusInfoProvider 注册");

        // Запуск定时保活задача（车机必需，始终Вкл启)
        KeepAliveManager.startKeepAliveWork(this);
        AppLog.d(TAG, "定时保活задачаЗапущено");
        
        // 防止休眠（только当Вкл启"Вкл机自Запуск"时)
        // WakeLock 主要  CameraForegroundService 维护
        // 这里作为резервное копирование，确保 Activity существует时также有 WakeLock
        if (appConfig.isAutoStartOnBoot()) {
            WakeUpHelper.acquirePersistentWakeLock(this);
            AppLog.d(TAG, "WakeLock Получение（Вкл机自ЗапускВкл启)");
        } else {
            AppLog.d(TAG, "WakeLock Не Получение（Вкл机自ЗапускНе Вкл启)");
        }
        
        // ЗапускХранилищеОчистка задача（Если 用户Настройки限制)
        storageCleanupManager = new StorageCleanupManager(this);
        storageCleanupManager.start();
        
        // ЗапускФайл传输Сервис（用于USB-накопитель转写入режим)
        FileTransferManager.getInstance(this).start();

        // проверка 否 Вкл机自Запуск
        boolean autoStartFromBoot = getIntent().getBooleanExtra("auto_start_from_boot", false);
        if (autoStartFromBoot) {
            // очистка标志，避免后续重复检测
            getIntent().removeExtra("auto_start_from_boot");
            
            // 判断 否необходимо移 до Фоновый режим：
            // - Если Вкл启автоматическиЗапись：不移 до Фоновый режим，显示主界面并Начать запись
            // - Если Не Вкл启автоматическиЗапись（只Вкл启悬浮窗/推送等)：移 до Фоновый режим
            if (appConfig.isAutoStartRecording()) {
                AppLog.d(TAG, "Вкл机自Запускрежим：Вкл启автоматическиЗапись，保持Передний план显示");
                shouldMoveToBackgroundOnReady = false;
            } else {
                AppLog.d(TAG, "Вкл机自Запускрежим：Не Вкл启автоматическиЗапись，ожидание窗口准备好后移 до Фоновый режим");
                // Настройки标志，ожидание onWindowFocusChanged 时再移 до Фоновый режим
                // 这确保 Activity 完全инициализация后再выполнение，避免断инициализация过程
                shouldMoveToBackgroundOnReady = true;
            }
        }

        // проверка 否有Запуск时传入 Удалённыйкоманда（冷Запуск)
        handleRemoteCommandFromIntent(getIntent());

        // Запуск悬浮窗Сервис（Если Включено)
        if (appConfig.isFloatingWindowEnabled() && WakeUpHelper.hasOverlayPermission(this)) {
            FloatingWindowService.start(this);
            AppLog.d(TAG, "悬浮窗Сервис запущен");
            
            // 延迟ОтправкаТекущийСтатус（ожиданиеСервисЗапускзавершение)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                // ОтправкаТекущийЗаписьСтатус
                broadcastCurrentRecordingState();
                // Приложение Передний план，隐藏悬浮窗
                FloatingWindowService.sendAppForegroundState(this, true);
            }, 500);
        }

        // Запуск补盲选项Сервис (副屏/主屏悬浮窗/转 к 灯联动/模拟按钮/全景避让)
        // 定制键唤醒独立于补盲全局ВклВыкл，单独判断
        if ((appConfig.isBlindSpotGlobalEnabled()
                && (appConfig.isSecondaryDisplayEnabled() || appConfig.isMainFloatingEnabled()
                    || appConfig.isTurnSignalLinkageEnabled() || appConfig.isMockTurnSignalFloatingEnabled()
                    || appConfig.isAvmAvoidanceEnabled()))
                || appConfig.isCustomKeyWakeupEnabled()) {
            BlindSpotService.update(this);
            AppLog.d(TAG, "补盲选项Сервис запущен");
        }
        
        // инициализация息屏Запись检测
        initScreenStateReceiver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        AppLog.d(TAG, "onNewIntent called");
        
        // 处理Удалённыйкоманда
        handleRemoteCommandFromIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        
        // Если  Вкл机自Запускрежим，窗口准备好后автоматически移 до Фоновый режим
        if (hasFocus && shouldMoveToBackgroundOnReady) {
            AppLog.d(TAG, "Вкл机自Запуск：窗口绪，移 до Фоновый режим（无感Запуск)");
            shouldMoveToBackgroundOnReady = false;  // очистка标志，避免重复выполнение
            
            // 延迟移 до Фоновый режим，确保инициализациязавершение
            new android.os.Handler().postDelayed(() -> {
                moveTaskToBack(true);  // 将Приложение移 до Фоновый режим
                AppLog.d(TAG, "Приложение移 до Фоновый режим，Вкл机自Запускзавершение");
            }, 500);  // 延迟 500ms
        }
    }

    /**
     * 处理来自 Intent  Удалённыйкоманда
     * 由 WakeUpHelper Запуск时传入
     */
    private void handleRemoteCommandFromIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getStringExtra("remote_action");
        if (action == null || action.isEmpty()) {
            return;
        }

        AppLog.d(TAG, "Received remote command from intent: " + action);

        // 处理Передний план切换команда（不необходимоожиданиеКамера)
        if ("foreground".equals(action)) {
            intent.removeExtra("remote_action");
            AppLog.d(TAG, "Foreground command executed - app brought to front");
            // Activity 经 Запуск до Передний план，不необходимо额外операция
            return;
        }
        
        // 注意：Фоновый режимкоманда现 通过广播处理（WakeUpHelper.ACTION_MOVE_TO_BACKGROUND)
        // 不再通过 startActivity 方式，避免闪屏问题

        // 先切换 до 主界面（Запись界面)，确保显示正确 界面
        showRecordingInterface();
        AppLog.d(TAG, "Switched to recording interface");

        // проверка 否  Telegram команда
        String remoteSource = intent.getStringExtra("remote_source");
        if ("telegram".equals(remoteSource)) {
            long chatId = intent.getLongExtra("telegram_chat_id", 0);
            int duration = intent.getIntExtra("remote_duration", 60);
            
            // очистка Intent  команда，避免重复выполнение
            intent.removeExtra("remote_action");
            intent.removeExtra("remote_source");
            
            AppLog.d(TAG, "Telegram command: action=" + action + ", chatId=" + chatId + ", duration=" + duration);
            
            // 标记有待处理 Удалённыйкоманда
            pendingRemoteCommand = true;
            
            // 判断 否应该 завершение后返回Фоновый режим
            boolean isRemoteWakeUpIntent = intent.getBooleanExtra("remote_wake_up", false);
            boolean wasAlreadyInForeground = hasBeenResumedOnce && !isInBackground;
            boolean shouldReturnToBackground = isRemoteWakeUpIntent && !isRecording && !wasAlreadyInForeground;
            
            if (shouldReturnToBackground) {
                isRemoteWakeUp = true;
                AppLog.d(TAG, "Telegram: Remote wake-up flag set, will return to background after completion");
            } else if (wasAlreadyInForeground) {
                isRemoteWakeUp = false;
                AppLog.d(TAG, "Telegram: App was in foreground, will stay in foreground after completion");
            } else {
                isRemoteWakeUp = false;
                AppLog.d(TAG, "Telegram: Recording in progress or no wake-up flag, staying in foreground");
            }
            
            // 延迟выполнениекоманда，ожиданиеКамера准备好
            int delay = wasAlreadyInForeground ? 1500 : 3000;
            final String finalAction = action;
            
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                pendingRemoteCommand = false;
                
                // проверкаКамера 否准备好
                if (cameraManager == null) {
                    AppLog.e(TAG, "Telegram: CameraManager is null");
                    executeTelegramCommand(finalAction, chatId, duration);
                    return;
                }
                
                int connectedCount = cameraManager.getConnectedCameraCount();
                AppLog.d(TAG, "Telegram: Connected cameras: " + connectedCount);
                
                // Если Подключение Камера不足，продолжитьожидание
                if (!cameraManager.hasConnectedCameras()) {
                    AppLog.w(TAG, "Telegram: No cameras connected yet, waiting 1.5s more...");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        boolean hasCamera = cameraManager != null && cameraManager.hasConnectedCameras();
                        AppLog.d(TAG, "Telegram: After waiting, hasConnectedCameras: " + hasCamera);
                        executeTelegramCommand(finalAction, chatId, duration);
                    }, 1500);
                } else {
                    AppLog.d(TAG, "Telegram: Cameras ready, executing command");
                    executeTelegramCommand(finalAction, chatId, duration);
                }
            }, delay);
            return;
        }

        // проверка 否 Feishuкоманда
        if ("feishu".equals(remoteSource)) {
            String chatId = intent.getStringExtra("feishu_chat_id");
            String messageId = intent.getStringExtra("feishu_message_id");
            int duration = intent.getIntExtra("remote_duration", 60);
            
            // очистка Intent  команда，避免重复выполнение
            intent.removeExtra("remote_action");
            intent.removeExtra("remote_source");
            
            AppLog.d(TAG, "Feishu command: action=" + action + ", chatId=" + chatId + ", duration=" + duration);
            
            // 标记有待处理 Удалённыйкоманда
            pendingRemoteCommand = true;
            
            // 判断 否应该 завершение后返回Фоновый режим
            boolean isRemoteWakeUpIntent = intent.getBooleanExtra("remote_wake_up", false);
            boolean wasAlreadyInForeground = hasBeenResumedOnce && !isInBackground;
            boolean shouldReturnToBackground = isRemoteWakeUpIntent && !isRecording && !wasAlreadyInForeground;
            
            if (shouldReturnToBackground) {
                isRemoteWakeUp = true;
                AppLog.d(TAG, "Feishu: Remote wake-up flag set, will return to background after completion");
            } else if (wasAlreadyInForeground) {
                isRemoteWakeUp = false;
                AppLog.d(TAG, "Feishu: App was in foreground, will stay in foreground after completion");
            } else {
                isRemoteWakeUp = false;
                AppLog.d(TAG, "Feishu: Recording in progress or no wake-up flag, staying in foreground");
            }
            
            // 延迟выполнениекоманда，ожиданиеКамера准备好
            int delay = wasAlreadyInForeground ? 1500 : 3000;
            final String finalAction = action;
            final String finalChatId = chatId;
            
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                pendingRemoteCommand = false;
                
                // проверкаКамера 否准备好
                if (cameraManager == null) {
                    AppLog.e(TAG, "Feishu: CameraManager is null");
                    executeFeishuCommand(finalAction, finalChatId, duration);
                    return;
                }
                
                int connectedCount = cameraManager.getConnectedCameraCount();
                AppLog.d(TAG, "Feishu: Connected cameras: " + connectedCount);
                
                // Если Подключение Камера不足，продолжитьожидание
                if (!cameraManager.hasConnectedCameras()) {
                    AppLog.w(TAG, "Feishu: No cameras connected yet, waiting 1.5s more...");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        boolean hasCamera = cameraManager != null && cameraManager.hasConnectedCameras();
                        AppLog.d(TAG, "Feishu: After waiting, hasConnectedCameras: " + hasCamera);
                        executeFeishuCommand(finalAction, finalChatId, duration);
                    }, 1500);
                } else {
                    AppLog.d(TAG, "Feishu: Cameras ready, executing command");
                    executeFeishuCommand(finalAction, finalChatId, duration);
                }
            }, delay);
            return;
        }
        
        // 提取DingTalk参数
        String conversationId = intent.getStringExtra("remote_conversation_id");
        String conversationType = intent.getStringExtra("remote_conversation_type");
        String userId = intent.getStringExtra("remote_user_id");
        int duration = intent.getIntExtra("remote_duration", 60);

        // очистка Intent  команда，避免重复выполнение
        intent.removeExtra("remote_action");

        // 标记有待处理 Удалённыйкоманда
        pendingRemoteCommand = true;
        
        // 判断 否应该 завершение后返回Фоновый режим
        // 逻辑：
        // 1. Если Выполняется Запись，保持Передний план（用户可能Выполняется использование)
        // 2. Если  Intent 有 remote_wake_up=true（ от  WakeUpHelper 发起)：
        //    - Если Приложениедо Передний план（hasBeenResumedOnce=true 且 isInBackground=false)，保持Передний план
        //    - 否则  от Фоновый режим唤醒 ，返回Фоновый режим
        boolean isRemoteWakeUpIntent = intent.getBooleanExtra("remote_wake_up", false);
        boolean wasAlreadyInForeground = hasBeenResumedOnce && !isInBackground;
        boolean shouldReturnToBackground = isRemoteWakeUpIntent && !isRecording && !wasAlreadyInForeground;
        
        if (shouldReturnToBackground) {
            isRemoteWakeUp = true;
            AppLog.d(TAG, "Remote wake-up flag set, will return to background after completion");
        } else if (isRecording) {
            isRemoteWakeUp = false;
            AppLog.d(TAG, "Recording in progress, will stay in foreground after completion");
        } else if (wasAlreadyInForeground) {
            isRemoteWakeUp = false;
            AppLog.d(TAG, "App was already in foreground, will stay in foreground after completion");
        } else {
            isRemoteWakeUp = false;
            AppLog.d(TAG, "No remote_wake_up flag, will stay in foreground");
        }

        // 延迟выполнениекоманда，ожиданиеКамера准备好
        int delay = wasAlreadyInForeground ? 1500 : 3000;
        
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            pendingRemoteCommand = false;
            
            // проверкаКамера 否准备好
            if (cameraManager == null) {
                AppLog.e(TAG, "CameraManager is null");
                executeRemoteCommand(action, conversationId, conversationType, userId, duration);
                return;
            }
            
            int connectedCount = cameraManager.getConnectedCameraCount();
            AppLog.d(TAG, "Connected cameras: " + connectedCount + "/4");
            
            // Если Подключение Камера少于4 шт.，продолжитьожидание
            if (connectedCount < 4) {
                AppLog.w(TAG, "Only " + connectedCount + " cameras connected, waiting 1.5s more...");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    int finalCount = cameraManager.getConnectedCameraCount();
                    AppLog.d(TAG, "After waiting, connected cameras: " + finalCount + "/4");
                    if (finalCount < 4) {
                        AppLog.w(TAG, "Still only " + finalCount + " cameras ready, executing anyway");
                    }
                    executeRemoteCommand(action, conversationId, conversationType, userId, duration);
                }, 1500);
            } else {
                AppLog.d(TAG, "All 4 cameras ready, executing command");
                executeRemoteCommand(action, conversationId, conversationType, userId, duration);
            }
        }, delay);
    }

    /**
     * выполнениеУдалённыйкоманда
     */
    private void executeRemoteCommand(String action, String conversationId, 
            String conversationType, String userId, int duration) {
        AppLog.d(TAG, "Executing remote command: " + action);
        
        if ("record".equals(action)) {
            AppLog.d(TAG, "Starting remote recording for " + duration + " seconds");
            if (remoteCommandDispatcher != null) {
                remoteCommandDispatcher.startDingTalkRecording(conversationId, conversationType, userId, duration);
            }
        } else if ("photo".equals(action)) {
            AppLog.d(TAG, "Taking remote photo");
            if (remoteCommandDispatcher != null) {
                remoteCommandDispatcher.startDingTalkPhoto(conversationId, conversationType, userId);
            }
        } else if ("start_recording".equals(action)) {
            AppLog.d(TAG, "Starting persistent recording (like button click)");
            executeStartPersistentRecording();
        } else if ("stop_recording".equals(action)) {
            AppLog.d(TAG, "Stopping recording and moving to background");
            executeStopRecordingAndBackground();
        } else {
            AppLog.w(TAG, "Unknown remote action: " + action);
        }
    }

    /**
     * выполнение Telegram Удалённыйкоманда
     */
    private void executeTelegramCommand(String action, long chatId, int duration) {
        AppLog.d(TAG, "Executing Telegram command: " + action);
        
        if ("record".equals(action)) {
            AppLog.d(TAG, "Telegram: Starting remote recording for " + duration + " seconds");
            if (remoteCommandDispatcher != null) {
                remoteCommandDispatcher.startTelegramRecording(chatId, duration);
            }
        } else if ("photo".equals(action)) {
            AppLog.d(TAG, "Telegram: Taking remote photo");
            if (remoteCommandDispatcher != null) {
                remoteCommandDispatcher.startTelegramPhoto(chatId);
            }
        } else {
            AppLog.w(TAG, "Telegram: Unknown action: " + action);
        }
    }

    /**
     * выполнениеFeishuУдалённыйкоманда
     */
    private void executeFeishuCommand(String action, String chatId, int duration) {
        AppLog.d(TAG, "Executing Feishu command: " + action);
        
        if ("record".equals(action)) {
            AppLog.d(TAG, "Feishu: Starting remote recording for " + duration + " seconds");
            if (remoteCommandDispatcher != null) {
                remoteCommandDispatcher.startFeishuRecording(chatId, duration);
            }
        } else if ("photo".equals(action)) {
            AppLog.d(TAG, "Feishu: Taking remote photo");
            if (remoteCommandDispatcher != null) {
                remoteCommandDispatcher.startFeishuPhoto(chatId);
            }
        } else {
            AppLog.w(TAG, "Feishu: Unknown action: " + action);
        }
    }
    
    /**
     * выполнениеЗапускНепрерывная запись（等同点击Запись按钮)
     */
    private void executeStartPersistentRecording() {
        if (isRecording) {
            AppLog.d(TAG, "Already recording, skip");
            return;
        }
        
        startRecording();
        AppLog.d(TAG, "Persistent recording started");
        
        // Начать запись后不退 до Фоновый режим，保持Передний план
        isRemoteWakeUp = false;
    }
    
    /**
     * выполнениеОстановить запись并退 до Фоновый режим
     */
    private void executeStopRecordingAndBackground() {
        if (!isRecording) {
            AppLog.d(TAG, "Not recording, just move to background");
            moveTaskToBack(true);
            return;
        }
        
        stopRecording();
        AppLog.d(TAG, "Запись остановлена");
        
        // 延迟退 до Фоновый режим
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            moveTaskToBack(true);
            AppLog.d(TAG, "Moved to background");
        }, 1000);
    }

    private void adjustFontScale(float scale) {
        android.content.res.Configuration configuration = getResources().getConfiguration();
        configuration.fontScale = scale;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        getBaseContext().getResources().updateConfiguration(configuration, metrics);
    }
    
    /**
     * 根据车型конфигурацияНастройки布局
     */
    private void setupLayoutByCarModel() {
        // По умолчаниюиспользование4Камера布局（GalaxyE5专用)
        int layoutId = R.layout.activity_main;
        configuredCameraCount = 4;
        requiredTextureCount = 4;

        String carModel = appConfig.getCarModel();
        
        // GalaxyE5-Мульти-кнопки：横屏布局，左侧按钮列表
        if (AppConfig.CAR_MODEL_E5_MULTI.equals(carModel)) {
            layoutId = R.layout.activity_main_e5_multi;
            configuredCameraCount = 4;
            requiredTextureCount = 4;
            AppLog.d(TAG, "использованиеGalaxyE5-Мульти-кнопкиконфигурация：横屏左侧按钮列表布局");
        }
        // GalaxyL6/L7：竖屏四宫格布局
        else if (AppConfig.CAR_MODEL_L7.equals(carModel)) {
            layoutId = R.layout.activity_main_l7;
            configuredCameraCount = 4;
            requiredTextureCount = 4;
            AppLog.d(TAG, "использованиеGalaxyL6/L7конфигурация：竖屏四宫格布局");
        }
        // GalaxyL7-Мульти-кнопки：竖屏四宫格布局（顶部多функция按钮)
        else if (AppConfig.CAR_MODEL_L7_MULTI.equals(carModel)) {
            layoutId = R.layout.activity_main_l7_multi;
            configuredCameraCount = 4;
            requiredTextureCount = 4;
            AppLog.d(TAG, "использованиеGalaxyL7-Мульти-кнопкиконфигурация：竖屏四宫格+顶部快捷按钮布局");
        }
        // Телефон：自适应2Камера布局
        else if (AppConfig.CAR_MODEL_PHONE.equals(carModel)) {
            layoutId = R.layout.activity_main_phone;
            configuredCameraCount = 2;
            requiredTextureCount = 2;
            AppLog.d(TAG, "использованиеТелефонконфигурация：自适应2Камера布局");
        }
        // 26 Starship7：横屏四Камера布局（基于GalaxyE5布局)
        else if (AppConfig.CAR_MODEL_XINGHAN_7.equals(carModel)) {
            layoutId = R.layout.activity_main;
            configuredCameraCount = 4;
            requiredTextureCount = 4;
            AppLog.d(TAG, "использование26 Starship7конфигурация：横屏4Камера布局");
        }
        // Мульти-камерный вид：自定义布局 + 圆角UI + 车辆控制
        else if (appConfig.isMultiviewCarModel()) {
            layoutId = R.layout.activity_main_multiview;
            configuredCameraCount = appConfig.getCameraCount();
            requiredTextureCount = configuredCameraCount;
            AppLog.d(TAG, "использованиеМульти-камерный вид：" + configuredCameraCount + "Камера");
        }
        // Своя модель：использование统一 自定义布局（поддержка自由操控)
        else if (appConfig.isCustomCarModel()) {
            layoutId = R.layout.activity_main_custom;
            configuredCameraCount = appConfig.getCameraCount();
            requiredTextureCount = configuredCameraCount;
            AppLog.d(TAG, "использованиеСвоя модель布局：" + configuredCameraCount + "Камера");
        }
        // GalaxyE5：横屏四Камера布局
        else {
            AppLog.d(TAG, "использованиеGalaxyE5По умолчаниюконфигурация：4Камера布局");
        }

        setContentView(layoutId);
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // НастройкиСтатус栏颜色为菜单栏背景色
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.menu_background));

            // 根据Текущий主题режимНастройкиСтатус栏图标颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    // 夜间режим：очистка浅色Статус栏标志，использование深色图标变为浅色图标
                    getWindow().getDecorView().setSystemUiVisibility(0);
                } else {
                    //  д.间режим：НастройкиСтатус栏图标为深色（因为背景 浅色)
                    getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
                }
            }
        }
        
        // только针 Телефон布局添加沉浸式Статус栏совместимость
        String carModel = appConfig.getCarModel();
        if (AppConfig.CAR_MODEL_PHONE.equals(carModel)) {
            View mainLayout = findViewById(R.id.main);
            if (mainLayout != null) {
                final int originalPaddingTop = mainLayout.getPaddingTop();
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
                    int statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                    v.setPadding(v.getPaddingLeft(), statusBarHeight + originalPaddingTop, v.getPaddingRight(), v.getPaddingBottom());
                    return insets;
                });
                androidx.core.view.ViewCompat.requestApplyInsets(mainLayout);
            }
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        recordingLayout = findViewById(R.id.main);
        fragmentContainer = findViewById(R.id.fragment_container);
        
        // Настройки导航头部版本号
        if (navigationView != null) {
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView versionText = headerView.findViewById(R.id.nav_header_version);
                if (versionText != null) {
                    try {
                        String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        versionText.setText("Версия: v" + versionName);
                    } catch (Exception e) {
                        // 忽略аномалия，保持По умолчанию文本
                    }
                }
            }
        }

        // 根据布局ПолучениеTextureView（不同布局有不同数量 TextureView)
        textureFront = findViewById(R.id.texture_front);
        textureBack = findViewById(R.id.texture_back);  // 1布局为null
        textureLeft = findViewById(R.id.texture_left);  // 1 и 2布局为null
        textureRight = findViewById(R.id.texture_right);  // 1 и 2布局为null
        
        btnStartRecord = findViewById(R.id.btn_start_record);
        btnExit = findViewById(R.id.btn_exit);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        
        // инициализацияЗаписьСтатус显示
        tvRecordingStats = findViewById(R.id.tv_recording_stats);
        initRecordingStatsDisplay();

        // инициализацияотладкаИнформация覆盖层
        tvDebugOverlay = findViewById(R.id.tv_debug_overlay);
        initDebugOverlayTapDetection();
        
        // обновлениеКамера标签（Если  Своя модель)
        updateCameraLabels();

        // инициализация自定义布局управление器（Если  Своя модель)
        initCustomLayoutManager();

        // 菜单按钮点击事件（部分布局可能没有此按钮)
        View btnMenu = findViewById(R.id.btn_menu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }
        
        // Мульти-кнопки布局 快捷导航按钮（только  L7-Мульти-кнопки 布局существует)
        View btnVideoPlayback = findViewById(R.id.btn_video_playback);
        if (btnVideoPlayback != null) {
            btnVideoPlayback.setOnClickListener(v -> showPlaybackInterface());
        }
        
        View btnPhotoPlayback = findViewById(R.id.btn_photo_playback);
        if (btnPhotoPlayback != null) {
            btnPhotoPlayback.setOnClickListener(v -> showPhotoPlaybackInterface());
        }
        
        View btnRemoteView = findViewById(R.id.btn_remote_view);
        if (btnRemoteView != null) {
            btnRemoteView.setOnClickListener(v -> showRemoteViewInterface());
        }
        
        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsInterface());
        }
        
        // E5-Мульти-кнопки布局 快捷导航按钮
        View btnPlayback = findViewById(R.id.btn_playback);
        if (btnPlayback != null) {
            btnPlayback.setOnClickListener(v -> showPlaybackInterface());
        }
        
        View btnPhotos = findViewById(R.id.btn_photos);
        if (btnPhotos != null) {
            btnPhotos.setOnClickListener(v -> showPhotoPlaybackInterface());
        }

        // Запись按钮：点击切换ЗаписьСтатус
        btnStartRecord.setOnClickListener(v -> toggleRecording());

        // Выход按钮：完全Выход из приложения
        btnExit.setOnClickListener(v -> exitApp());

        btnTakePhoto.setOnClickListener(v -> takePicture());

        if (textureFront != null) {
            textureFront.setSurfaceTextureListener(buildSurfaceListener("front"));
        }
        if (textureBack != null && configuredCameraCount >= 2) {
            textureBack.setSurfaceTextureListener(buildSurfaceListener("back"));
        }
        if (textureLeft != null && configuredCameraCount >= 4) {
            textureLeft.setSurfaceTextureListener(buildSurfaceListener("left"));
        }
        if (textureRight != null && configuredCameraCount >= 4) {
            textureRight.setSurfaceTextureListener(buildSurfaceListener("right"));
        }
    }

    private TextureView.SurfaceTextureListener buildSurfaceListener(String cameraKey) {
        return new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {
                textureReadyCount++;
                AppLog.d(TAG, "TextureView " + cameraKey + " ready: " + textureReadyCount + "/" + requiredTextureCount);

                if (textureReadyCount >= requiredTextureCount && checkPermissions()) {
                    if (cameraManager == null) {
                        initCamera();
                    } else {
                        cameraManager.updatePreviewTextureViews(textureFront, textureBack, textureLeft, textureRight);
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {
                AppLog.d(TAG, "TextureView " + cameraKey + " size changed: " + width + "x" + height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull android.graphics.SurfaceTexture surface) {
                textureReadyCount--;
                AppLog.d(TAG, "TextureView " + cameraKey + " destroyed, remaining: " + textureReadyCount);
                if (cameraManager != null) {
                    cameraManager.onPreviewTextureDestroyed(cameraKey);
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull android.graphics.SurfaceTexture surface) {
            }
        };
    }
    
    /**
     * обновлениеКамера标签
     * 统一использование AppConfig.getCameraName()  值，确保主界面 и Настройки界面显示一致
     */
    private void updateCameraLabels() {
        // Получение标签控件（根据布局可能существуетилине существует)
        TextView labelFront = findViewById(R.id.label_front);
        TextView labelBack = findViewById(R.id.label_back);
        TextView labelLeft = findViewById(R.id.label_left);
        TextView labelRight = findViewById(R.id.label_right);
        
        // Настройки自定义名称，Если 名称пусто则隐藏标签
        if (labelFront != null) {
            updateCameraLabel(labelFront, appConfig.getCameraName("front"));
        }
        if (labelBack != null && configuredCameraCount >= 2) {
            updateCameraLabel(labelBack, appConfig.getCameraName("back"));
        }
        if (labelLeft != null && configuredCameraCount >= 4) {
            updateCameraLabel(labelLeft, appConfig.getCameraName("left"));
        }
        if (labelRight != null && configuredCameraCount >= 4) {
            updateCameraLabel(labelRight, appConfig.getCameraName("right"));
        }
    }
    
    /**
     * обновление单 шт.Камера标签，Если 名称пусто则隐藏
     */
    private void updateCameraLabel(TextView label, String name) {
        if (name == null || name.trim().isEmpty()) {
            label.setVisibility(View.GONE);
        } else {
            label.setText(name);
            label.setVisibility(View.VISIBLE);
        }
    }

    /**
     * инициализация自定义布局управление器（только Своя модель时действует)
     * 业务逻辑委托  CustomLayoutManager 处理
     */
    private void initCustomLayoutManager() {
        if (!appConfig.needsCustomLayoutManager()) {
            return;
        }

        // Получение视图引用
        android.widget.FrameLayout frameFront = findViewById(R.id.frame_front);
        android.widget.FrameLayout frameBack = findViewById(R.id.frame_back);
        android.widget.FrameLayout frameLeft = findViewById(R.id.frame_left);
        android.widget.FrameLayout frameRight = findViewById(R.id.frame_right);
        android.widget.FrameLayout frameVehicleControl = findViewById(R.id.frame_vehicle_control);
        View editControls = findViewById(R.id.edit_controls);
        View containerCameras = findViewById(R.id.container_cameras);
        
        // 按钮容器根据方 к Выбрать
        String buttonOrientation = appConfig.getCustomButtonOrientation();
        boolean isVertical = AppConfig.BUTTON_ORIENTATION_VERTICAL.equals(buttonOrientation);
        android.view.ViewGroup buttonContainer = isVertical ? 
            findViewById(R.id.container_buttons_left) : 
            findViewById(R.id.container_buttons_bottom);

        // 根据Камера数量隐藏不необходимо 容器
        if (configuredCameraCount < 4) {
            if (frameLeft != null) frameLeft.setVisibility(View.GONE);
            if (frameRight != null) frameRight.setVisibility(View.GONE);
            if (frameVehicleControl != null) frameVehicleControl.setVisibility(View.GONE);
        }
        if (configuredCameraCount < 2) {
            if (frameBack != null) frameBack.setVisibility(View.GONE);
        }

        // 动态загрузка按钮布局
        setupCustomButtonLayout(buttonContainer);

        // инициализация布局управление器（所有业务逻辑由 Manager 处理)
        customLayoutManager = new CustomLayoutManager(this);
        customLayoutManager.setCameraCount(configuredCameraCount);
        customLayoutManager.setOnButtonLayoutChangeListener(orientation -> {
            // 重新загрузка按钮布局
            android.view.ViewGroup newContainer = orientation.equals(AppConfig.BUTTON_ORIENTATION_VERTICAL) ?
                    findViewById(R.id.container_buttons_left) : findViewById(R.id.container_buttons_bottom);
            setupCustomButtonLayout(newContainer);
            
            // обновление布局управление器 按钮容器引用
            customLayoutManager.updateButtonContainer(newContainer);
        });
        customLayoutManager.setupFloatingViews(
                frameFront, frameBack, frameLeft, frameRight, frameVehicleControl,
                buttonContainer, editControls, containerCameras,
                textureFront, textureBack, textureLeft, textureRight);

        AppLog.d(TAG, "自定义布局управление器инициализациязавершение");
    }

    /**
     * Настройки自定义按钮布局
     * 根据конфигурация动态загрузка按钮样式 и 方 к 
     */
    private void setupCustomButtonLayout(android.view.ViewGroup ignoredContainer) {
        // Получениеконфигурация
        String buttonStyle = appConfig.getCustomButtonStyle();
        String buttonOrientation = appConfig.getCustomButtonOrientation();
        boolean isVertical = AppConfig.BUTTON_ORIENTATION_VERTICAL.equals(buttonOrientation);
        
        AppLog.d(TAG, "按钮конфигурация读取: style=" + buttonStyle + " (standard=" + AppConfig.BUTTON_STYLE_STANDARD + "), orientation=" + buttonOrientation);
        
        // Получение两 шт.按钮容器
        android.widget.FrameLayout leftContainer = findViewById(R.id.container_buttons_left);
        android.widget.FrameLayout bottomContainer = findViewById(R.id.container_buttons_bottom);
        
        if (leftContainer == null || bottomContainer == null) {
            AppLog.e(TAG, "Button containers not found");
            return;
        }
        
        // очистка两 шт.容器
        leftContainer.removeAllViews();
        bottomContainer.removeAllViews();
        
        // Выбрать布局资源
        int layoutResId;
        boolean isStandard = AppConfig.BUTTON_STYLE_STANDARD.equals(buttonStyle);
        AppLog.d(TAG, "按钮样式判断: buttonStyle='" + buttonStyle + "', STANDARD='" + AppConfig.BUTTON_STYLE_STANDARD + "', isStandard=" + isStandard);
        
        if (isStandard) {
            // Стандартные кнопки（E5风格图标按钮)
            layoutResId = isVertical ? 
                R.layout.layout_custom_buttons_standard_vertical : 
                R.layout.layout_custom_buttons_standard;
            AppLog.d(TAG, ">>> использованиеСтандартные кнопки布局(图标) - " + (isVertical ? "Вертикальная" : "Горизонтальная") + ", layoutResId=" + layoutResId);
        } else {
            // Мульти-кнопки（文字按钮)
            layoutResId = isVertical ? 
                R.layout.layout_custom_buttons_multi_vertical : 
                R.layout.layout_custom_buttons_multi;
            AppLog.d(TAG, ">>> использованиеМульти-кнопки布局(文字) - " + (isVertical ? "Вертикальная" : "Горизонтальная") + ", layoutResId=" + layoutResId);
        }
        
        // загрузка布局 до 正确 容器
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        View buttonsView = inflater.inflate(layoutResId, null, false);
        
        android.view.ViewGroup targetContainer;
        if (isVertical) {
            // Вертикальная：按钮 左侧
            leftContainer.addView(buttonsView);
            leftContainer.setVisibility(View.VISIBLE);
            bottomContainer.setVisibility(View.GONE);
            targetContainer = leftContainer;
        } else {
            // Горизонтальная：按钮 底部
            bottomContainer.addView(buttonsView);
            bottomContainer.setVisibility(View.VISIBLE);
            leftContainer.setVisibility(View.GONE);
            targetContainer = bottomContainer;
        }

        // 重新Получение按钮引用
        btnStartRecord = targetContainer.findViewById(R.id.btn_start_record);
        btnExit = targetContainer.findViewById(R.id.btn_exit);
        btnTakePhoto = targetContainer.findViewById(R.id.btn_take_photo);

        // Настройки按钮点击事件
        if (btnStartRecord != null) {
            btnStartRecord.setOnClickListener(v -> toggleRecording());
        }
        if (btnExit != null) {
            btnExit.setOnClickListener(v -> exitApp());
        }
        if (btnTakePhoto != null) {
            btnTakePhoto.setOnClickListener(v -> takePicture());
        }

        // НастройкиДругое快捷按钮
        View btnVideoPlayback = targetContainer.findViewById(R.id.btn_video_playback);
        if (btnVideoPlayback != null) {
            btnVideoPlayback.setOnClickListener(v -> showPlaybackInterface());
        }

        View btnPhotoPlayback = targetContainer.findViewById(R.id.btn_photo_playback);
        if (btnPhotoPlayback != null) {
            btnPhotoPlayback.setOnClickListener(v -> showPhotoPlaybackInterface());
        }

        View btnSettings = targetContainer.findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsInterface());
        }
        
        // 菜单按钮（Стандартные кнопки样式有此按钮)
        View btnMenu = targetContainer.findViewById(R.id.btn_menu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } else {
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                }
            });
        }
    }
    
    /**
     * инициализацияЗаписьСтатус显示
     */
    private void initRecordingStatsDisplay() {
        if (tvRecordingStats == null) {
            return;
        }
        
        //  от Настройкизагрузка显示ВклВыклСтатус
        isRecordingStatsEnabled = appConfig.isRecordingStatsEnabled();
        
        // инициализация计时器 Handler
        recordingTimerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        // 确保 View 可点击（т.е.使 INVISIBLE также能响应点击)
        tvRecordingStats.setClickable(true);
        tvRecordingStats.setFocusable(true);
        
        // Настройки双击切换显示/隐藏
        tvRecordingStats.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastStatsClickTime < DOUBLE_CLICK_INTERVAL) {
                // 双击：切换显示Статус
                toggleRecordingStatsDisplay();
                lastStatsClickTime = 0;  // Сброс，避免三连击触发
            } else {
                lastStatsClickTime = currentTime;
            }
            AppLog.d(TAG, "ЗаписьСтатус显示 点击, isRecording=" + isRecording + ", enabled=" + isRecordingStatsEnabled);
        });
    }
    
    /**
     * 切换ЗаписьСтатус显示 ВклВыкл
     */
    private void toggleRecordingStatsDisplay() {
        isRecordingStatsEnabled = !isRecordingStatsEnabled;
        appConfig.setRecordingStatsEnabled(isRecordingStatsEnabled);
        
        if (tvRecordingStats != null && isRecording) {
            if (isRecordingStatsEnabled) {
                // 显示Статус（использование alpha Восстановление可见)
                tvRecordingStats.setAlpha(1.0f);
                Toast.makeText(this, "Индикатор записи включён", Toast.LENGTH_SHORT).show();
            } else {
                // использование alpha=0 隐藏，但保持 VISIBLE Статус以响应点击
                tvRecordingStats.setAlpha(0.0f);
                Toast.makeText(this, "Индикатор записи выключен", Toast.LENGTH_SHORT).show();
            }
        }
        
        AppLog.d(TAG, "ЗаписьСтатус显示切换: " + (isRecordingStatsEnabled ? "Вкл启" : "Закрыть"));
    }
    
    /**
     * Начать запись计时器
     */
    private void startRecordingTimer() {
        startRecordingTimer(0, 1);  // использованиеПо умолчанию值， от 头Вкл始计时
    }
    
    /**
     * Начать запись计时器（поддержкаВосстановление)
     * @param savedStartTime Сохранить Вкл始时间（0表示 от Текущий时间Вкл始)
     * @param savedSegment Сохранить 分数
     */
    private void startRecordingTimer(long savedStartTime, int savedSegment) {
        if (savedStartTime > 0) {
            // Восстановлениережим：использованиеСохранить Вкл始时间
            recordingStartTime = savedStartTime;
            currentSegmentCount = savedSegment;
            AppLog.d(TAG, "ВосстановлениеЗапись计时器 - startTime=" + savedStartTime + ", segment=" + savedSegment);
        } else {
            // 新Запись：использованиеТекущий时间
            recordingStartTime = System.currentTimeMillis();
            currentSegmentCount = 1;
        }
        
        if (tvRecordingStats != null) {
            // 始终设为 VISIBLE，通过 alpha 控制可见性
            tvRecordingStats.setVisibility(View.VISIBLE);
            tvRecordingStats.setAlpha(isRecordingStatsEnabled ? 1.0f : 0.0f);
            updateRecordingStatsDisplay();
        }
        
        // 创建定时обновлениезадача
        recordingTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    updateRecordingStatsDisplay();
                    recordingTimerHandler.postDelayed(this, 1000);  // 每 сек.обновление一 раз
                }
            }
        };
        
        recordingTimerHandler.post(recordingTimerRunnable);
    }
    
    /**
     * Остановить запись计时器
     */
    private void stopRecordingTimer() {
        if (recordingTimerHandler != null && recordingTimerRunnable != null) {
            recordingTimerHandler.removeCallbacks(recordingTimerRunnable);
        }
        
        // 隐藏ЗаписьСтатус显示
        if (tvRecordingStats != null) {
            tvRecordingStats.setVisibility(View.GONE);
        }
        
        recordingStartTime = 0;
        currentSegmentCount = 1;
    }
    
    /**
     * обновлениеЗаписьСтатус显示
     */
    private void updateRecordingStatsDisplay() {
        if (tvRecordingStats == null) {
            return;
        }
        
        // 计算Запись时长
        long elapsedMs = System.currentTimeMillis() - recordingStartTime;
        long totalSeconds = elapsedMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        // 格式化时间：MM:SS / 分数（т.е.使隐藏такжеобновление文本，便于双击显示时立т.е.看 до 正确时间)
        String timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d / %d", minutes, seconds, currentSegmentCount);
        tvRecordingStats.setText(timeStr);
    }
    
    /**
     * 当分切换时调用，обновление分计数
     */
    public void onSegmentSwitch(int newSegmentIndex) {
        currentSegmentCount = newSegmentIndex + 1;  // 分索引 от 0Вкл始，显示 от 1Вкл始
        AppLog.d(TAG, "分切换: 第 " + currentSegmentCount + " ");
        
        // 立т.е.обновление显示
        runOnUiThread(this::updateRecordingStatsDisplay);
    }
    
    /**
     * ОбновитьЗаписьСтатус显示Настройки（ от Настройки界面返回时调用)
     */
    public void refreshRecordingStatsSettings() {
        isRecordingStatsEnabled = appConfig.isRecordingStatsEnabled();
        
        // Если Выполняется Запись，根据新Настройки显示или隐藏（通过 alpha 控制，保持可点击)
        if (isRecording && tvRecordingStats != null) {
            tvRecordingStats.setAlpha(isRecordingStatsEnabled ? 1.0f : 0.0f);
        }
    }

    /**
     * ПолучениеТекущий各Камера РазрешениеИнформация（供РазрешениеНастройки界面использование)
     * @return 格式化 РазрешениеИнформация字符串
     */
    public String getCurrentCameraResolutionsInfo() {
        if (cameraManager != null) {
            return cameraManager.getCameraResolutionsInfo();
        }
        return null;
    }

    /**
     * инициализацияУдалённыйкоманда分发器
     * Настройки CameraController  и  RecordingStateListener
     */
    private void initRemoteCommandDispatcher() {
        remoteCommandDispatcher = new RemoteCommandDispatcher(this);
        
        // НастройкиКамера控制器
        remoteCommandDispatcher.setCameraController(new RemoteCommandHandler.CameraController() {
            @Override
            public boolean isRecording() {
                return MainActivity.this.isRecording || (cameraManager != null && cameraManager.isRecording());
            }
            
            @Override
            public boolean hasConnectedCameras() {
                return cameraManager != null && cameraManager.hasConnectedCameras();
            }
            
            @Override
            public boolean startRecording(String timestamp) {
                if (cameraManager != null) {
                    return cameraManager.startRecording(timestamp);
                }
                return false;
            }
            
            @Override
            public void stopRecording(boolean skipTransfer) {
                if (cameraManager != null) {
                    cameraManager.stopRecording(skipTransfer);
                }
            }
            
            @Override
            public void takePicture(String timestamp) {
                if (cameraManager != null) {
                    cameraManager.takePicture(timestamp);
                }
            }
            
            @Override
            public void stopRecordingTimer() {
                MainActivity.this.stopRecordingTimer();
            }
            
            @Override
            public void stopBlinkAnimation() {
                MainActivity.this.stopBlinkAnimation();
            }
            
            @Override
            public void startRecording() {
                MainActivity.this.startRecording();
            }
            
            @Override
            public void setSegmentDurationOverride(long durationMs) {
                if (cameraManager != null) {
                    cameraManager.setSegmentDurationOverride(durationMs);
                }
            }
            
            @Override
            public void clearSegmentDurationOverride() {
                if (cameraManager != null) {
                    cameraManager.clearSegmentDurationOverride();
                }
            }
        });
        
        // НастройкиЗаписьСтатус监听器
        remoteCommandDispatcher.setRecordingStateListener(new RemoteCommandHandler.RecordingStateListener() {
            @Override
            public void onRemoteRecordingStart() {
                isRemoteRecording = true;
            }
            
            @Override
            public void onRemoteRecordingStop() {
                isRemoteRecording = false;
                isPreparingRecording = false;
                stopBlinkAnimation();
            }
            
            @Override
            public void onPreparing() {
                isPreparingRecording = true;
                showPreparingIndicator();
            }
            
            @Override
            public void onPreparingComplete() {
                isPreparingRecording = false;
                hidePreparingIndicator();
            }
            
            @Override
            public void returnToBackgroundIfRemoteWakeUp() {
                MainActivity.this.returnToBackgroundIfRemoteWakeUp();
            }
            
            @Override
            public boolean isRemoteWakeUp() {
                return MainActivity.this.isRemoteWakeUp;
            }
        });
        
        AppLog.d(TAG, "RemoteCommandDispatcher инициализациязавершение");
        
        //  от  RemoteServiceManager 同步РаботаСервис  API 客户端
        // 这确保 Activity 重建后，Удалённыйкоманда处理器能正确использование有  API 客户端
        syncApiClientsFromRemoteServiceManager();
    }
    
    /**
     *  от  RemoteServiceManager 同步РаботаСервис  API 客户端
     *   Activity 重建时，УдалённыйСервис可能 Работа，необходимо同步 до 新  remoteCommandDispatcher
     */
    private void syncApiClientsFromRemoteServiceManager() {
        if (remoteCommandDispatcher == null) {
            return;
        }
        
        RemoteServiceManager serviceManager = RemoteServiceManager.getInstance();
        
        // 同步DingTalk API 客户端
        DingTalkApiClient dingTalk = serviceManager.getDingTalkApiClient();
        if (dingTalk != null) {
            remoteCommandDispatcher.setDingTalkApiClient(dingTalk);
            this.dingTalkApiClient = dingTalk;  // 同时обновление本地引用
            this.dingTalkStreamManager = serviceManager.getDingTalkStreamManager();
            AppLog.d(TAG, " от  RemoteServiceManager 同步DingTalk API 客户端");
        }
        
        // 同步 Telegram API 客户端
        com.kooo.evcam.telegram.TelegramApiClient telegram = serviceManager.getTelegramApiClient();
        if (telegram != null) {
            remoteCommandDispatcher.setTelegramApiClient(telegram);
            this.telegramApiClient = telegram;
            this.telegramBotManager = serviceManager.getTelegramBotManager();
            AppLog.d(TAG, " от  RemoteServiceManager 同步 Telegram API 客户端");
        }
        
        // 同步Feishu API 客户端
        com.kooo.evcam.feishu.FeishuApiClient feishu = serviceManager.getFeishuApiClient();
        if (feishu != null) {
            remoteCommandDispatcher.setFeishuApiClient(feishu);
            this.feishuApiClient = feishu;
            this.feishuBotManager = serviceManager.getFeishuBotManager();
            AppLog.d(TAG, " от  RemoteServiceManager 同步Feishu API 客户端");
        }
    }
    
    /**
     * обновлениеУдалённыйкоманда分发器  API 客户端
     *  СервисЗапуск后调用
     */
    private void updateRemoteDispatcherApiClients() {
        if (remoteCommandDispatcher != null) {
            if (dingTalkApiClient != null) {
                remoteCommandDispatcher.setDingTalkApiClient(dingTalkApiClient);
            }
            if (telegramApiClient != null) {
                remoteCommandDispatcher.setTelegramApiClient(telegramApiClient);
            }
            if (feishuApiClient != null) {
                remoteCommandDispatcher.setFeishuApiClient(feishuApiClient);
            }
        }
    }

    /**
     * 切换侧边栏 открыть/ЗакрытоСтатус
     */
    public void toggleDrawer() {
        if (drawerLayout != null) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        }
    }

    /**
     * Настройки导航抽屉
     */
    private void setupNavigationDrawer() {
        // Настройки导航菜单点击监听
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            // 先очистка所有菜单项 选Статус（处理跨 групп选)
            clearAllNavigationChecks();
            
            if (itemId == R.id.nav_recording) {
                // 显示Запись界面
                showRecordingInterface();
            } else if (itemId == R.id.nav_playback) {
                // 显示回看界面
                showPlaybackInterface();
            } else if (itemId == R.id.nav_photo_playback) {
                // 显示Изображение回看界面
                showPhotoPlaybackInterface();
            } else if (itemId == R.id.nav_remote_view) {
                // 显示DingTalkУдалённый界面
                showRemoteViewInterface();
            } else if (itemId == R.id.nav_telegram) {
                // 显示 Telegram Удалённый界面
                showTelegramInterface();
            } else if (itemId == R.id.nav_feishu) {
                // 显示FeishuУдалённый界面
                showFeishuInterface();
            } else if (itemId == R.id.nav_heartbeat) {
                // 显示Мониторинг界面
                showHeartbeatInterface();
            } else if (itemId == R.id.nav_secondary_display) {
                // 显示补盲选项界面
                showBlindSpotInterface();
            } else if (itemId == R.id.nav_settings) {
                showSettingsInterface();
            }
            // НастройкиТекущий项为选
            navigationView.setCheckedItem(itemId);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // По умолчанию选Запись界面
        navigationView.setCheckedItem(R.id.nav_recording);
    }
    
    /**
     * очистка所有导航菜单项 选Статус
     * 用于处理跨 групп选时 Статус同步
     */
    private void clearAllNavigationChecks() {
        Menu menu = navigationView.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setChecked(false);
            // 处理子菜单
            if (item.hasSubMenu()) {
                SubMenu subMenu = item.getSubMenu();
                for (int j = 0; j < subMenu.size(); j++) {
                    subMenu.getItem(j).setChecked(false);
                }
            }
        }
    }

    /**
     * проверка并处理首 разЗапуск
     * 首 разЗапуск时автоматически进入Настройки界面并显示引导弹窗
     */
    private void checkFirstLaunch() {
        if (appConfig == null || !appConfig.isFirstLaunch()) {
            return;
        }

        AppLog.d(TAG, "Обнаружено首 разЗапуск，进入Настройки界面");

        // 标记首 разЗапускзавершение（ 显示弹窗前标记，避免重复触发)
        appConfig.setFirstLaunchCompleted();

        // 延迟выполнение，确保 UI 完全инициализация
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // 进入Настройки界面
            showSettingsInterface();
            clearAllNavigationChecks();
            navigationView.setCheckedItem(R.id.nav_settings);

            // 显示引导弹窗
            showFirstLaunchGuideDialog();
        }, 300);
    }

    /**
     * 显示首 разЗапуск引导弹窗（美化版)
     */
    private void showFirstLaunchGuideDialog() {
        // 创建自定义 话框
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_first_launch_guide);
        dialog.setCancelable(false);

        // Настройки 话框窗口属性
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            // Настройки背景透明（让圆角生效)
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // Настройки 话框宽度
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            window.setAttributes(params);
        }

        // загрузка二维码Изображение
        android.widget.ImageView ivQrcode = dialog.findViewById(R.id.iv_qrcode);
        loadQrcodeImage(ivQrcode);

        // НастройкиПодтвердить按钮点击事件
        dialog.findViewById(R.id.btn_confirm).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * загрузка打赏二维码Изображение（URL经过混淆处理)
     */
    private void loadQrcodeImage(android.widget.ImageView imageView) {
        // 根据屏幕密度动态Настройки二维码尺寸
        // НизкийDPI大屏设备использование更大尺寸，ВысокийDPI设备использование适尺寸
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;
        int screenWidthPx = dm.widthPixels;
        
        // 计算二维码尺寸（像素)
        // density: mdpi=1.0, hdpi=1.5, xhdpi=2.0, xxhdpi=3.0, xxxhdpi=4.0
        int qrcodeSizePx;
        if (density <= 1.0f) {
            // mdpi или更Низкий密度（大屏НизкийDPI设备)：использование屏幕宽度 25%
            qrcodeSizePx = (int) (screenWidthPx * 0.25f);
        } else if (density <= 1.5f) {
            // hdpi：использование屏幕宽度 22%
            qrcodeSizePx = (int) (screenWidthPx * 0.22f);
        } else if (density <= 2.0f) {
            // xhdpi：использование屏幕宽度 20%
            qrcodeSizePx = (int) (screenWidthPx * 0.20f);
        } else {
            // xxhdpi 及и выше（Высокий密度设备)：использование屏幕宽度 18%
            qrcodeSizePx = (int) (screenWidthPx * 0.18f);
        }
        
        // НастройкиImageView尺寸
        android.view.ViewGroup.LayoutParams params = imageView.getLayoutParams();
        params.width = qrcodeSizePx;
        params.height = qrcodeSizePx;
        imageView.setLayoutParams(params);
        
        // URL混淆Хранилище，防止 轻易изменение
        // 原始URL经过Base64编码后分Хранилище
        final String[] p = {
            "aHR0cHM6Ly9ldmNhbS5jaGF0d2Vi", // Первый
            "LmNsb3VkLzE3Njk0NzcxOTc4NTUu", // Второй  
            "anBn"                           // Третий
        };
        
        new Thread(() -> {
            try {
                //  групп合并解码URL
                String encoded = p[0] + p[1] + p[2];
                String url = new String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT));
                
                // скачиваниеИзображение
                java.net.URL imageUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) imageUrl.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoInput(true);
                conn.connect();
                
                java.io.InputStream is = conn.getInputStream();
                final android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();
                
                //  主线程обновлениеUI
                if (bitmap != null) {
                    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception e) {
                AppLog.e(TAG, "загрузка二维码ИзображениеОшибка: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 显示Запись界面
     */
    public void showRecordingInterface() {
        // очистка所有Fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        for (Fragment fragment : fragmentManager.getFragments()) {
            fragmentManager.beginTransaction().remove(fragment).commit();
        }

        // 显示Запись布局，隐藏Fragment容器
        recordingLayout.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
    }

    /**
     * 公Всего 方法：返回预览/Запись界面
     * 供 Fragment  主页按钮调用
     */
    public void goToRecordingInterface() {
        // Закрыто侧边栏（Если открыть 话)
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        showRecordingInterface();
        // обновление导航菜单选Статус（先очистка所有选，再НастройкиТекущий项)
        if (navigationView != null) {
            clearAllNavigationChecks();
            navigationView.setCheckedItem(R.id.nav_recording);
        }
    }

    /**
     * 显示回看界面（新版四宫格界面)
     */
    private void showPlaybackInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示新版PlaybackFragment（поддержка四宫格预览)
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new PlaybackFragmentNew());
        transaction.commit();
    }

    /**
     * 显示Изображение回看界面（新版四宫格界面)
     */
    private void showPhotoPlaybackInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示新版PhotoPlaybackFragment（поддержка四宫格预览)
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new PhotoPlaybackFragmentNew());
        transaction.commit();
    }

    /**
     * 显示DingTalkУдалённыйПросмотр界面
     */
    private void showRemoteViewInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示RemoteViewFragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new RemoteViewFragment());
        transaction.commit();
    }

    /**
     * 显示 Telegram Удалённый界面
     */
    private void showTelegramInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示 TelegramFragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new TelegramFragment());
        transaction.commit();
    }

    /**
     * 显示FeishuУдалённый界面
     */
    private void showFeishuInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示 FeishuFragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new FeishuFragment());
        transaction.commit();
    }

    /**
     * 显示Мониторинг界面
     */
    private void showHeartbeatInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示 HeartbeatFragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new com.kooo.evcam.heartbeat.HeartbeatFragment());
        transaction.commit();
    }
    
    /**
     * 显示软件Настройки界面
     */
    private void showSettingsInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示SettingsFragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new SettingsFragment());
        transaction.commit();
    }

    /**
     * 显示补盲选项Настройки界面
     */
    private void showBlindSpotInterface() {
        // 隐藏Запись布局，显示Fragment容器
        recordingLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示BlindSpotSettingsFragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new BlindSpotSettingsFragment());
        transaction.commit();
    }


    private boolean checkPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                AppLog.d(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        AppLog.d(TAG, "Requesting permissions...");
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (checkPermissions()) {
                // Разрешениепредоставить，但необходимоожиданиеTextureView准备好
                // Если TextureView经准备好，立т.е.инициализацияКамера
                if (textureReadyCount >= requiredTextureCount) {
                    initCamera();
                }
            } else {
                Toast.makeText(this, "Требуются разрешения камеры и хранилища", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initCamera() {
        // 确保所有необходимо TextureViewвсе准备好
        if (textureReadyCount < requiredTextureCount) {
            AppLog.w(TAG, "Not all TextureViews are ready yet: " + textureReadyCount + "/" + requiredTextureCount);
            return;
        }
        
        // 防止重复инициализация：Если  cameraManager 经существует，直接返回
        if (cameraManager != null) {
            AppLog.d(TAG, "Camera already initialized, skipping");
            return;
        }

        // проверка Holder  否有Фоновый режиминициализация 实例
        com.kooo.evcam.camera.CameraManagerHolder holder = com.kooo.evcam.camera.CameraManagerHolder.getInstance();
        MultiCameraManager existingManager = holder.getCameraManager();
        if (existingManager != null) {
            // Фоновый режиминициализация，复用实例并绑定 TextureView
            AppLog.d(TAG, "复用Фоновый режиминициализация Камерауправление器，绑定 TextureView");
            cameraManager = existingManager;

            // --- 补全Фоновый режиминициализация时缺失 回调 ---
            // Фоновый режим（BlindSpotService)инициализация  MultiCameraManager 没有Настройки MainActivity  回调，
            // 必须 此处Настройки，否则左Правая камераПоворот 变换、Запись计时等функция不нормально。

            // КамераСтатус回调
            cameraManager.setStatusCallback((cameraId, status) -> {
                AppLog.d(TAG, "Камера " + cameraId + ": " + status);
                if (status.contains("Ошибка") || status.contains("отключено")) {
                    runOnUiThread(() -> {
                        if (status.contains("ERROR_CAMERA_IN_USE") || status.contains("DISCONNECTED")) {
                            Toast.makeText(MainActivity.this,
                                "Камера " + cameraId + " занята, переподключение...",
                                Toast.LENGTH_SHORT).show();
                        } else if (status.contains("max reconnect attempts")) {
                            Toast.makeText(MainActivity.this,
                                "Камера " + cameraId + " переподключение не удалось, перезапустите приложение",
                                Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

            // 分切换回调
            cameraManager.setSegmentSwitchCallback(newSegmentIndex -> {
                onSegmentSwitch(newSegmentIndex);
            });

            // 损坏Файл删除回调
            cameraManager.setCorruptedFilesCallback(deletedFiles -> {
                showCorruptedFilesDeletedDialog(deletedFiles);
            });

            // Codec 回退Уведомление回调
            cameraManager.setCodecFallbackCallback(() -> {
                runOnUiThread(() -> {
                    Toast.makeText(this,
                        "Ошибка записи, переключено на MediaCodec. При частых ошибках измените режим записи вручную",
                        Toast.LENGTH_LONG).show();
                });
            });

            // Запись时间戳обновление回调
            cameraManager.setTimestampUpdateCallback(newTimestamp -> {
                if (isRemoteRecording && remoteRecordingTimestamp != null) {
                    AppLog.d(TAG, "Удалённая запись时间戳обновление: " + remoteRecordingTimestamp + " -> " + newTimestamp);
                    remoteRecordingTimestamp = newTimestamp;
                }
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.onTimestampUpdated(newTimestamp);
                }
            });

            // 首 раз数据写入回调（Запись计时器依赖此回调)
            cameraManager.setFirstDataWrittenCallback(() -> {
                AppLog.d(TAG, "Получена команда: 首 раз数据写入回调，Запись真正Вкл始");
                runOnUiThread(() -> {
                    if (isPreparingRecording) {
                        isPreparingRecording = false;
                        hidePreparingIndicator();
                        AppLog.d(TAG, "准备Статусзавершить，Запись进入нормальноСтатус");
                    }
                    if (isRecording && !isRemoteRecording) {
                        if (shouldResumeRecordingAfterRecreate && savedRecordingStartTime > 0) {
                            startRecordingTimer(savedRecordingStartTime, savedSegmentCount);
                            AppLog.d(TAG, "主题切换后ВосстановлениеЗапись计时器（首 раз写入后)");
                            shouldResumeRecordingAfterRecreate = false;
                            savedRecordingStartTime = 0;
                            savedSegmentCount = 1;
                        } else {
                            startRecordingTimer();
                            AppLog.d(TAG, "вручнуюЗапись计时器Запущено（首 раз写入后)");
                        }
                    }
                    if (remoteCommandDispatcher != null) {
                        remoteCommandDispatcher.onFirstDataWritten();
                    }
                    if (isRemoteRecording && pendingRemoteDurationSeconds > 0) {
                        AppLog.d(TAG, "Удалённая запись首 раз写入Успешно，Запуск " + pendingRemoteDurationSeconds + "  сек.定时器");
                        autoStopHandler.postDelayed(autoStopRunnable, pendingRemoteDurationSeconds * 1000L);
                        pendingRemoteDurationSeconds = 0;
                    }
                });
            });

            // 预览尺寸回调（Выкл键：负责左Правая камераПоворот 变换)
            cameraManager.setPreviewSizeCallback((cameraKey, cameraId, previewSize) -> {
                AppLog.d(TAG, "Камера " + cameraId + " 预览尺寸: " + previewSize.getWidth() + "x" + previewSize.getHeight());
                runOnUiThread(() -> {
                    final AutoFitTextureView textureView;
                    switch (cameraKey) {
                        case "front": textureView = textureFront; break;
                        case "back":  textureView = textureBack;  break;
                        case "left":  textureView = textureLeft;  break;
                        case "right": textureView = textureRight; break;
                        default:      textureView = null;         break;
                    }
                    if (textureView != null) {
                        applyPreviewSizeTransform(cameraKey, textureView, previewSize);
                    }
                });
            });

            // 绑定 TextureView
            cameraManager.updatePreviewTextureViews(textureFront, textureBack, textureLeft, textureRight);

            // открыть所有Камера（Фоновый режиминициализация时только创建 象，可能只открыть补盲所需 单 шт.Камера)
            // 主界面необходимо所有Камера画面，открыть Камера会  openCamera Внутреннее 防重复проверка跳过
            cameraManager.openAllCameras();

            // вручную触发 previewSizeCallback（Камера可能 补盲阶открыть并确定预览尺寸)
            cameraManager.firePreviewSizeCallbacks();

            // инициализация亮度/Шумоподавление调节управление器
            imageAdjustManager = new ImageAdjustManager(this);
            registerCamerasToImageAdjustManager();
            initHeartbeatManager();
            AppLog.d(TAG, "Camera initialized with " + configuredCameraCount + " cameras (reused from background)");
            checkResumeRecordingAfterRecreate();
            checkAutoStartRecording();
            startAutoRecordingCheck();
            return;
        }

        cameraManager = new MultiCameraManager(this);
        cameraManager.setMaxOpenCameras(configuredCameraCount);
        // 注册 до 全局 Holder
        holder.setCameraManager(cameraManager);
        
        // инициализация亮度/Шумоподавление调节управление器
        imageAdjustManager = new ImageAdjustManager(this);

        // НастройкиКамераСтатус回调
        cameraManager.setStatusCallback((cameraId, status) -> {
            AppLog.d(TAG, "Камера " + cameraId + ": " + status);

            // Если Камераотключеноили 占用，Уведомление用户
            if (status.contains("Ошибка") || status.contains("отключено")) {
                runOnUiThread(() -> {
                    if (status.contains("ERROR_CAMERA_IN_USE") || status.contains("DISCONNECTED")) {
                        Toast.makeText(MainActivity.this,
                            "Камера " + cameraId + " занята, автоматическое переподключение...",
                            Toast.LENGTH_SHORT).show();
                    } else if (status.contains("max reconnect attempts")) {
                        Toast.makeText(MainActivity.this,
                            "Камера " + cameraId + " переподключение не удалось, перезапустите приложение",
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        // Настройки分切换回调
        cameraManager.setSegmentSwitchCallback(newSegmentIndex -> {
            onSegmentSwitch(newSegmentIndex);
        });

        // Настройки损坏Файл删除回调
        cameraManager.setCorruptedFilesCallback(deletedFiles -> {
            showCorruptedFilesDeletedDialog(deletedFiles);
        });

        // Настройки Codec 回退Уведомление回调
        cameraManager.setCodecFallbackCallback(() -> {
            runOnUiThread(() -> {
                Toast.makeText(this, 
                    "Ошибка записи, переключено на MediaCodec. При частых ошибках измените режим записи вручную", 
                    Toast.LENGTH_LONG).show();
            });
        });

        // НастройкиЗапись时间戳обновление回调
        // 当 Watchdog 触发重建Запись时，时间戳会改变，необходимообновление以便正确查找ВидеоФайл
        cameraManager.setTimestampUpdateCallback(newTimestamp -> {
            if (isRemoteRecording && remoteRecordingTimestamp != null) {
                AppLog.d(TAG, "Удалённая запись时间戳обновление: " + remoteRecordingTimestamp + " -> " + newTimestamp);
                remoteRecordingTimestamp = newTimestamp;
            }
            // Уведомление RemoteCommandDispatcher обновление时间戳（新重构代码)
            if (remoteCommandDispatcher != null) {
                remoteCommandDispatcher.onTimestampUpdated(newTimestamp);
            }
        });

        // Настройки首 раз数据写入回调
        // 用于 Камера真正Вкл始输出数据后Запуск计时器（分计时、DingTalkЗапись计时等)
        cameraManager.setFirstDataWrittenCallback(() -> {
            AppLog.d(TAG, "Получена команда: 首 раз数据写入回调，Запись真正Вкл始");
            runOnUiThread(() -> {
                // завершить"准备"Статус
                if (isPreparingRecording) {
                    isPreparingRecording = false;
                    hidePreparingIndicator();
                    AppLog.d(TAG, "准备Статусзавершить，Запись进入нормальноСтатус");
                }
                
                // Начать запись计时器（ от 首 раз写入Вкл始计时，而不  от Запись求Вкл始)
                // 这样右角显示 时间 "действуетЗапись时长"
                if (isRecording && !isRemoteRecording) {
                    // проверка 否 主题切换后Восстановление Запись
                    if (shouldResumeRecordingAfterRecreate && savedRecordingStartTime > 0) {
                        // использованиеСохранить 时间Восстановление计时器（计时不Сброс)
                        startRecordingTimer(savedRecordingStartTime, savedSegmentCount);
                        AppLog.d(TAG, "主题切换后ВосстановлениеЗапись计时器（首 раз写入后)");
                        // СбросВосстановление标志
                        shouldResumeRecordingAfterRecreate = false;
                        savedRecordingStartTime = 0;
                        savedSegmentCount = 1;
                    } else {
                        startRecordingTimer();
                        AppLog.d(TAG, "вручнуюЗапись计时器Запущено（首 раз写入后)");
                    }
                }
                
                // Если  Удалённая запись，Уведомление RemoteCommandDispatcher Запуск定时器
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.onFirstDataWritten();
                }
                
                // совместимость旧逻辑：Если  Удалённая запись，现 才Запуск定时器
                if (isRemoteRecording && pendingRemoteDurationSeconds > 0) {
                    AppLog.d(TAG, "Удалённая запись首 раз写入Успешно，Запуск " + pendingRemoteDurationSeconds + "  сек.定时器");
                    autoStopHandler.postDelayed(autoStopRunnable, pendingRemoteDurationSeconds * 1000L);
                    pendingRemoteDurationSeconds = 0;  // Сброс
                }
            });
        });

        // Настройки预览尺寸回调
        cameraManager.setPreviewSizeCallback((cameraKey, cameraId, previewSize) -> {
            AppLog.d(TAG, "Камера " + cameraId + " 预览尺寸: " + previewSize.getWidth() + "x" + previewSize.getHeight());
            runOnUiThread(() -> {
                final AutoFitTextureView textureView;
                switch (cameraKey) {
                    case "front": textureView = textureFront; break;
                    case "back":  textureView = textureBack;  break;
                    case "left":  textureView = textureLeft;  break;
                    case "right": textureView = textureRight; break;
                    default:      textureView = null;         break;
                }
                if (textureView != null) {
                    applyPreviewSizeTransform(cameraKey, textureView, previewSize);
                }
            });
        });

        // ожиданиеTextureView准备好
        textureFront.post(() -> {
            try {
                // 检测Доступно Камера
                CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String[] cameraIds = cm.getCameraIdList();

                AppLog.d(TAG, "========== Камера诊断Информация ==========");
                AppLog.d(TAG, "Available cameras: " + cameraIds.length);

                for (String id : cameraIds) {
                    AppLog.d(TAG, "---------- Camera ID: " + id + " ----------");

                    try {
                        android.hardware.camera2.CameraCharacteristics characteristics = cm.getCameraCharacteristics(id);

                        // 打印Камера方 к 
                        Integer facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                        String facingStr = "UNKNOWN";
                        if (facing != null) {
                            switch (facing) {
                                case android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT:
                                    facingStr = "FRONT";
                                    break;
                                case android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK:
                                    facingStr = "BACK";
                                    break;
                                case android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL:
                                    facingStr = "EXTERNAL";
                                    break;
                            }
                        }
                        AppLog.d(TAG, "  Facing: " + facingStr);

                        // 打印Поддерживаемые 输出格式 и Разрешение
                        android.hardware.camera2.params.StreamConfigurationMap map =
                            characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                        if (map != null) {
                            // 打印 ImageFormat.PRIVATE  Разрешение
                            android.util.Size[] privateSizes = map.getOutputSizes(android.graphics.ImageFormat.PRIVATE);
                            if (privateSizes != null && privateSizes.length > 0) {
                                AppLog.d(TAG, "  PRIVATE formats (" + privateSizes.length + " sizes):");
                                for (int i = 0; i < Math.min(privateSizes.length, 5); i++) {
                                    AppLog.d(TAG, "    [" + i + "] " + privateSizes[i].getWidth() + "x" + privateSizes[i].getHeight());
                                }
                                if (privateSizes.length > 5) {
                                    AppLog.d(TAG, "    ... and " + (privateSizes.length - 5) + " more");
                                }
                            }

                            // 打印 ImageFormat.YUV_420_888  Разрешение
                            android.util.Size[] yuvSizes = map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888);
                            if (yuvSizes != null && yuvSizes.length > 0) {
                                AppLog.d(TAG, "  YUV_420_888 formats (" + yuvSizes.length + " sizes):");
                                for (int i = 0; i < Math.min(yuvSizes.length, 5); i++) {
                                    AppLog.d(TAG, "    [" + i + "] " + yuvSizes[i].getWidth() + "x" + yuvSizes[i].getHeight());
                                }
                                if (yuvSizes.length > 5) {
                                    AppLog.d(TAG, "    ... and " + (yuvSizes.length - 5) + " more");
                                }
                            }

                            // 打印 SurfaceTexture  Разрешение
                            android.util.Size[] textureSizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);
                            if (textureSizes != null && textureSizes.length > 0) {
                                AppLog.d(TAG, "  SurfaceTexture formats (" + textureSizes.length + " sizes):");
                                for (int i = 0; i < Math.min(textureSizes.length, 5); i++) {
                                    AppLog.d(TAG, "    [" + i + "] " + textureSizes[i].getWidth() + "x" + textureSizes[i].getHeight());
                                }
                                if (textureSizes.length > 5) {
                                    AppLog.d(TAG, "    ... and " + (textureSizes.length - 5) + " more");
                                }
                            }
                        } else {
                            AppLog.w(TAG, "  StreamConfigurationMap is NULL!");
                        }

                        // 打印硬件级别
                        Integer hwLevel = characteristics.get(android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                        String hwLevelStr = "UNKNOWN";
                        if (hwLevel != null) {
                            switch (hwLevel) {
                                case android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                                    hwLevelStr = "LEGACY";
                                    break;
                                case android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                                    hwLevelStr = "LIMITED";
                                    break;
                                case android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                                    hwLevelStr = "FULL";
                                    break;
                                case android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                                    hwLevelStr = "LEVEL_3";
                                    break;
                            }
                        }
                        AppLog.d(TAG, "  Hardware Level: " + hwLevelStr);

                    } catch (Exception e) {
                        AppLog.e(TAG, "  Error getting characteristics for camera " + id + ": " + e.getMessage());
                    }
                }

                AppLog.d(TAG, "========================================");

                // 根据车型конфигурацияинициализацияКамера
                String carModel = appConfig.getCarModel();
                if (AppConfig.CAR_MODEL_L7.equals(carModel) || AppConfig.CAR_MODEL_L7_MULTI.equals(carModel)) {
                    // GalaxyL6/L7 / L7-Мульти-кнопки：использование固定映射
                    initCamerasForL7(cameraIds);
                } else if (AppConfig.CAR_MODEL_PHONE.equals(carModel)) {
                    // Телефонрежим：2Камера（前+后)
                    initCamerasForPhone(cameraIds);
                } else if (AppConfig.CAR_MODEL_XINGHAN_7.equals(carModel)) {
                    // 26 Starship7：использование固定映射（前3后2左4右1)
                    initCamerasForXinghan7(cameraIds);
                } else if (appConfig.needsCustomLayoutManager()) {
                    // Своя модель/多视角：использование用户конфигурация Камера映射
                    initCamerasForCustomModel(cameraIds);
                } else {
                    // GalaxyE5：использование固定映射
                    initCamerasForGalaxyE5(cameraIds);
                }
                
                // 根据Настройки决定Записьрежим（поддержка用户вручнуюВыбрать)
                boolean useCodecRecording = appConfig.shouldUseCodecRecording();
                cameraManager.setCodecRecordingMode(useCodecRecording);
                String recordingMode = appConfig.getRecordingMode();
                String modeDesc = useCodecRecording ? "MediaCodec" : "MediaRecorder";
                AppLog.d(TAG, "Записьрежим: " + modeDesc + " (Настройки: " + recordingMode + ")");

                // открыть所有Камера
                cameraManager.openAllCameras();
                
                // 注册Камера до 亮度/Шумоподавление调节управление器
                registerCamerasToImageAdjustManager();
                
                // инициализацияМониторингуправление器
                initHeartbeatManager();

                AppLog.d(TAG, "Camera initialized with " + configuredCameraCount + " cameras");
                //Toast.makeText(this, "открыть " + configuredCameraCount + " камер(ы)", Toast.LENGTH_SHORT).show();
                
                // проверка 否необходимоВосстановлениеЗапись（主题切换后)，优先级Высокий于автоматическиЗапись
                checkResumeRecordingAfterRecreate();
                
                // проверка并触发автоматическиЗапись（延迟выполнение，确保Камера准备绪)
                checkAutoStartRecording();
                
                // ЗапускавтоматическиЗаписьПлановая проверка（Если ВключитьавтоматическиЗапись)
                startAutoRecordingCheck();

            } catch (CameraAccessException e) {
                AppLog.e(TAG, "Failed to access camera", e);
                Toast.makeText(this, "Ошибка доступа к камере: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * GalaxyE5车型：использование固定 Камера映射
     */
    private void initCamerasForGalaxyE5(String[] cameraIds) {
        if (cameraIds.length >= 4) {
            // 有4 шт.или更多Камера
            // 修正КамераПозиция映射：前=cameraIds[2], 后=cameraIds[1], 左=cameraIds[3], 右=cameraIds[0]
            cameraManager.initCameras(
                    cameraIds[2], textureFront,  // 前Камераиспользование cameraIds[2]
                    cameraIds[1], textureBack,   // Задняя камераиспользование cameraIds[1]
                    cameraIds[3], textureLeft,   // Левая камераиспользование cameraIds[3]
                    cameraIds[0], textureRight   // Правая камераиспользование cameraIds[0]
            );
        } else if (cameraIds.length >= 2) {
            // 只有2 шт.Камера，复用 до 四 шт.Позиция
            // 注意：参数顺序必须 и  initCameras(frontId, frontView, backId, backView, leftId, leftView, rightId, rightView)  应
            cameraManager.initCameras(
                    null, null,
                    null, null,                    
                    cameraIds[0], textureLeft,   // leftПозицияиспользование textureLeft
                    cameraIds[1], textureRight   // rightПозицияиспользование textureRight
            );
        } else if (cameraIds.length == 1) {
            // 只有1 шт.Камера，所有Позицияиспользование同一 шт.
            cameraManager.initCameras(
                    cameraIds[0], textureFront,
                    cameraIds[0], textureBack,
                    cameraIds[0], textureLeft,
                    cameraIds[0], textureRight
            );
        } else {
            Toast.makeText(this, "Нет доступных камер", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * GalaxyL6/L7车型：использование固定 Камера映射（竖屏四宫格)
     * 前=2, 后=3, 左=0, 右=1
     */
    private void initCamerasForL7(String[] cameraIds) {
        if (cameraIds.length >= 4) {
            // 有4 шт.или更多Камера
            cameraManager.initCameras(
                    cameraIds[2], textureFront,  // 前Камераиспользование cameraIds[2]
                    cameraIds[3], textureBack,   // Задняя камераиспользование cameraIds[3]
                    cameraIds[0], textureLeft,   // Левая камераиспользование cameraIds[0]
                    cameraIds[1], textureRight   // Правая камераиспользование cameraIds[1]
            );
        } else if (cameraIds.length >= 2) {
            // 只有2 шт.Камера，复用 до 四 шт.Позиция
            cameraManager.initCameras(
                    cameraIds[0], textureFront,
                    cameraIds[1], textureBack,
                    cameraIds[0], textureLeft,
                    cameraIds[1], textureRight
            );
        } else if (cameraIds.length == 1) {
            // 只有1 шт.Камера，所有Позицияиспользование同一 шт.
            cameraManager.initCameras(
                    cameraIds[0], textureFront,
                    cameraIds[0], textureBack,
                    cameraIds[0], textureLeft,
                    cameraIds[0], textureRight
            );
        } else {
            Toast.makeText(this, "Нет доступных камер", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 26 Starship7车型：использование固定 Камера映射
     * 前=3, 后=2, 左=4, 右=1
     */
    private void initCamerasForXinghan7(String[] cameraIds) {
        if (cameraIds.length >= 5) {
            // 有5 шт.или更多Камера
            cameraManager.initCameras(
                    cameraIds[3], textureFront,  // 前Камераиспользование cameraIds[3]
                    cameraIds[2], textureBack,   // Задняя камераиспользование cameraIds[2]
                    cameraIds[4], textureLeft,   // Левая камераиспользование cameraIds[4]
                    cameraIds[1], textureRight   // Правая камераиспользование cameraIds[1]
            );
        } else if (cameraIds.length >= 4) {
            // 只有4 шт.Камера，использованиеДоступно ID
            cameraManager.initCameras(
                    cameraIds[3], textureFront,
                    cameraIds[2], textureBack,
                    cameraIds[0], textureLeft,
                    cameraIds[1], textureRight
            );
        } else if (cameraIds.length >= 2) {
            // 只有2 шт.Камера，复用 до 四 шт.Позиция
            cameraManager.initCameras(
                    cameraIds[0], textureFront,
                    cameraIds[1], textureBack,
                    cameraIds[0], textureLeft,
                    cameraIds[1], textureRight
            );
        } else if (cameraIds.length == 1) {
            // 只有1 шт.Камера，所有Позицияиспользование同一 шт.
            cameraManager.initCameras(
                    cameraIds[0], textureFront,
                    cameraIds[0], textureBack,
                    cameraIds[0], textureLeft,
                    cameraIds[0], textureRight
            );
        } else {
            Toast.makeText(this, "Нет доступных камер", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Телефонрежим：использование前后2 шт.Камера
     *  и GalaxyE5不同，Телефон布局只有 textureFront  и  textureBack
     */
    private void initCamerasForPhone(String[] cameraIds) {
        if (cameraIds.length >= 2) {
            // 有2 шт.или更多Камера：использование前后两 шт.Камера
            // 通常 cameraIds[0]  ЗадняяКамера，cameraIds[1]  ФронтальнаяКамера
            cameraManager.initCameras(
                    cameraIds[1], textureFront,  // ФронтальнаяКамера（通常 ID=1)
                    cameraIds[0], textureBack,   // ЗадняяКамера（通常 ID=0)
                    null, null,
                    null, null
            );
            AppLog.d(TAG, "Телефонрежиминициализация：Фронтальная=" + cameraIds[1] + ", Задняя=" + cameraIds[0]);
        } else if (cameraIds.length == 1) {
            // 只有1 шт.Камера，前后использование同一 шт.
            cameraManager.initCameras(
                    cameraIds[0], textureFront,
                    cameraIds[0], textureBack,
                    null, null,
                    null, null
            );
            AppLog.d(TAG, "Телефонрежиминициализация：单Камера=" + cameraIds[0]);
        } else {
            Toast.makeText(this, "Нет доступных камер", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Своя модель：использование用户конфигурация Камера映射
     */
    private void initCamerasForCustomModel(String[] cameraIds) {
        // Получение用户конфигурация КамераID
        String frontId = appConfig.getCameraId("front");
        String backId = appConfig.getCameraId("back");
        String leftId = appConfig.getCameraId("left");
        String rightId = appConfig.getCameraId("right");
        
        AppLog.d(TAG, "Своя модельконфигурация - Камера数量: " + configuredCameraCount);
        AppLog.d(TAG, "  前: " + frontId + ", 后: " + backId + ", 左: " + leftId + ", 右: " + rightId);
        
        switch (configuredCameraCount) {
            case 1:
                // 1Камерарежим
                if (textureFront != null) {
                    cameraManager.initCameras(
                            frontId, textureFront,
                            null, null,
                            null, null,
                            null, null
                    );
                }
                break;
            case 2:
                // 2Камерарежим
                if (textureFront != null && textureBack != null) {
                    cameraManager.initCameras(
                            frontId, textureFront,
                            backId, textureBack,
                            null, null,
                            null, null
                    );
                }
                break;
            default:
                // 4Камерарежим
                if (textureFront != null && textureBack != null && textureLeft != null && textureRight != null) {
                    cameraManager.initCameras(
                            frontId, textureFront,
                            backId, textureBack,
                            leftId, textureLeft,
                            rightId, textureRight
                    );

                    // Настройки自定义Поворот 角度（только用于Своя модель)
                    setCustomRotationForCameras();
                }
                break;
        }
    }

    /**
     * 为Своя модель КамераНастройкиПоворот 角度
     * 注意：自定义布局По умолчанию不Поворот 、不镜像，所有调节 自由调节界面进行
     */
    private void setCustomRotationForCameras() {
        if (!appConfig.needsCustomLayoutManager()) {
            return;  // 只 Своя модель/多视角Приложение
        }

        // 自定义布局：По умолчанию不Приложение任何Поворот ，保持原始Статус
        // 所有Поворот 、镜像等调节все 自由调节界面进行
        AppLog.d(TAG, "Своя модель：保持Камера原始Статус，不ПриложениеавтоматическиПоворот ");
        
        // 明确Настройки所有КамераПоворот 为0
        if (cameraManager != null) {
            SingleCamera frontCamera = cameraManager.getCamera("front");
            SingleCamera backCamera = cameraManager.getCamera("back");
            SingleCamera leftCamera = cameraManager.getCamera("left");
            SingleCamera rightCamera = cameraManager.getCamera("right");

            if (frontCamera != null) frontCamera.setCustomRotation(0);
            if (backCamera != null) backCamera.setCustomRotation(0);
            if (leftCamera != null) leftCamera.setCustomRotation(0);
            if (rightCamera != null) rightCamera.setCustomRotation(0);
        }
    }

    /**
     *   TextureView ПриложениеПоворот 变换 (修正版 - 解决变形问题)
     * @param textureView 要Поворот   TextureView
     * @param previewSize 预览尺寸（原始  1280x800)
     * @param rotation Поворот 角度（90 или 270)
     * @param cameraKey Камера标识
     */
    /**
     * ПриложениеТелефон缩放变换，保持Камера预览 宽Высокий比不 拉伸
     */
    private void applyPhoneScaleTransform(AutoFitTextureView textureView, android.util.Size previewSize, String cameraKey) {
        textureView.post(() -> {
            int viewWidth = textureView.getWidth();
            int viewHeight = textureView.getHeight();

            if (viewWidth == 0 || viewHeight == 0) {
                AppLog.d(TAG, cameraKey + " TextureView 尺寸为0，延迟Приложение缩放");
                textureView.postDelayed(() -> applyPhoneScaleTransform(textureView, previewSize, cameraKey), 100);
                return;
            }

            int previewWidth = previewSize.getWidth();
            int previewHeight = previewSize.getHeight();

            android.graphics.Matrix matrix = new android.graphics.Matrix();

            float centerX = viewWidth / 2f;
            float centerY = viewHeight / 2f;

            // 计算缩放比例，использование FIT_CENTER 策略（保持比例，完整显示)
            float scaleX = (float) viewWidth / previewWidth;
            float scaleY = (float) viewHeight / previewHeight;
            float scale = Math.min(scaleX, scaleY);  // 取较小值，确保完整显示

            // 计算缩放后 尺寸
            float scaledWidth = previewWidth * scale;
            float scaledHeight = previewHeight * scale;

            // 计算偏移量，使内容居
            float dx = (viewWidth - scaledWidth) / 2f;
            float dy = (viewHeight - scaledHeight) / 2f;

            // Настройки变换矩阵：先缩放，再平移居
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);

            // Сохранить基础变换，并叠加预览矫正
            previewBaseTransforms.put(cameraKey, new android.graphics.Matrix(matrix));
            PreviewCorrection.postApply(matrix, appConfig, cameraKey, viewWidth, viewHeight);

            textureView.setTransform(matrix);
            AppLog.d(TAG, cameraKey + " ПриложениеТелефон缩放变换: view=" + viewWidth + "x" + viewHeight + 
                    ", preview=" + previewWidth + "x" + previewHeight + 
                    ", scale=" + scale);
        });
    }

    /**
     * 根据车型 и КамераПозиция，  TextureView Приложение正确 宽Высокий比 и Поворот 变换。
     *  от  previewSizeCallback 提取，避免нормальноинициализация и Фоновый режим复用Путь 代码重复。
     */
    private void applyPreviewSizeTransform(String cameraKey, AutoFitTextureView textureView, android.util.Size previewSize) {
        String carModel = appConfig.getCarModel();

        if (appConfig.needsCustomLayoutManager()) {
            textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            textureView.setFillContainer(true);
            AppLog.d(TAG, "Настройки " + cameraKey + " 宽Высокий比(自定义-填充): " + previewSize.getWidth() + "x" + previewSize.getHeight());
            if (customLayoutManager != null) {
                customLayoutManager.updateCameraAspectRatio(cameraKey, previewSize.getWidth(), previewSize.getHeight(), 0);
            }
            applyPreviewCorrectionOnly(textureView, cameraKey);
        } else if (AppConfig.CAR_MODEL_L7.equals(carModel) || AppConfig.CAR_MODEL_L7_MULTI.equals(carModel)) {
            boolean needRotation = "left".equals(cameraKey) || "right".equals(cameraKey);
            if (needRotation) {
                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                AppLog.d(TAG, "Настройки " + cameraKey + " 宽Высокий比(Поворот 后): " + previewSize.getHeight() + ":" + previewSize.getWidth());
                int rotation = "left".equals(cameraKey) ? 270 : 90;
                applyRotationTransform(textureView, previewSize, rotation, cameraKey);
            } else {
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                textureView.setFillContainer(false);
                AppLog.d(TAG, "Настройки " + cameraKey + " 宽Высокий比: " + previewSize.getWidth() + ":" + previewSize.getHeight() + ", 适应режим");
                applyPreviewCorrectionOnly(textureView, cameraKey);
            }
        } else if (AppConfig.CAR_MODEL_PHONE.equals(carModel)) {
            textureView.setFillContainer(false);
            applyPhoneScaleTransform(textureView, previewSize, cameraKey);
            AppLog.d(TAG, "Настройки " + cameraKey + " Телефон缩放变换, 预览尺寸: " + previewSize.getWidth() + "x" + previewSize.getHeight());
        } else {
            // E5 等Другое车型
            boolean needRotation = "left".equals(cameraKey) || "right".equals(cameraKey);
            if (needRotation) {
                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                AppLog.d(TAG, "Настройки " + cameraKey + " 宽Высокий比(E5Поворот 后): " + previewSize.getHeight() + ":" + previewSize.getWidth());
                int rotation = "left".equals(cameraKey) ? 270 : 90;
                applyRotationTransform(textureView, previewSize, rotation, cameraKey);
            } else {
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                boolean useFillMode = configuredCameraCount >= 4;
                if (useFillMode) {
                    textureView.setFillContainer(true);
                    AppLog.d(TAG, "Настройки " + cameraKey + " 宽Высокий比: " + previewSize.getWidth() + ":" + previewSize.getHeight() + ", 填满режим");
                } else {
                    textureView.setFillContainer(false);
                    AppLog.d(TAG, "Настройки " + cameraKey + " 宽Высокий比: " + previewSize.getWidth() + ":" + previewSize.getHeight() + ", 适应режим");
                }
                applyPreviewCorrectionOnly(textureView, cameraKey);
            }
        }
    }

    private void applyRotationTransform(AutoFitTextureView textureView, android.util.Size previewSize,
                                        int rotation, String cameraKey) {
        // 延迟выполнение，确保 TextureView 经завершение布局
        textureView.post(() -> {
            int viewWidth = textureView.getWidth();
            int viewHeight = textureView.getHeight();

            if (viewWidth == 0 || viewHeight == 0) {
                AppLog.d(TAG, cameraKey + " TextureView 尺寸为0，延迟ПриложениеПоворот ");
                // Если 视图还没有尺寸，再 раз延迟
                textureView.postDelayed(() -> applyRotationTransform(textureView, previewSize, rotation, cameraKey), 100);
                return;
            }

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            android.graphics.RectF viewRect = new android.graphics.RectF(0, 0, viewWidth, viewHeight);
            
            // 缓冲区矩形，использование float 精度
            android.graphics.RectF bufferRect = new android.graphics.RectF(0, 0, previewSize.getWidth(), previewSize.getHeight());

            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            if (rotation == 90 || rotation == 270) {
                // 1. 将 bufferRect 心移动 до  viewRect 心
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                
                // 2. 将 buffer 映射 до  view，这一步会处理拉伸校正
                matrix.setRectToRect(viewRect, bufferRect, android.graphics.Matrix.ScaleToFit.FILL);
                
                // 3. 计算缩放比例以填满屏幕 (Center Crop)
                // 因为Поворот  90 度，所以 viewHeight  应 previewWidth，viewWidth  应 previewHeight
                float scale = Math.max(
                        (float) viewHeight / previewSize.getWidth(),
                        (float) viewWidth / previewSize.getHeight());
                
                // 4. Приложение缩放
                matrix.postScale(scale, scale, centerX, centerY);
                
                // 5. ПриложениеПоворот 
                matrix.postRotate(rotation, centerX, centerY);
            } else if (android.view.Surface.ROTATION_180 == rotation) {
                // Если необходимо处理 180 度翻转
                matrix.postRotate(180, centerX, centerY);
            }

            // Сохранить基础变换，并叠加预览矫正
            previewBaseTransforms.put(cameraKey, new android.graphics.Matrix(matrix));
            PreviewCorrection.postApply(matrix, appConfig, cameraKey, viewWidth, viewHeight);

            textureView.setTransform(matrix);
            AppLog.d(TAG, cameraKey + " Приложение修正Поворот : " + rotation + "度");
        });
    }

    /**
     *  没有基础变换  TextureView 单独Приложение预览矫正
     * 用于 E5/L7 前Задняя камера、Своя модель等不необходимоПоворот  场景
     */
    private void applyPreviewCorrectionOnly(AutoFitTextureView textureView, String cameraKey) {
        textureView.post(() -> {
            int viewWidth = textureView.getWidth();
            int viewHeight = textureView.getHeight();
            if (viewWidth <= 0 || viewHeight <= 0) {
                textureView.postDelayed(() -> applyPreviewCorrectionOnly(textureView, cameraKey), 100);
                return;
            }
            android.graphics.Matrix matrix = new android.graphics.Matrix(); // identity
            previewBaseTransforms.put(cameraKey, new android.graphics.Matrix(matrix));
            PreviewCorrection.postApply(matrix, appConfig, cameraKey, viewWidth, viewHeight);
            textureView.setTransform(matrix);
        });
    }

    /**
     * Обновить所有预览 TextureView  矫正变换
     * 由悬浮窗调参илиНастройки页调用
     */
    public void refreshPreviewCorrection() {
        runOnUiThread(() -> {
            refreshSinglePreviewCorrection(textureFront, "front");
            refreshSinglePreviewCorrection(textureBack, "back");
            refreshSinglePreviewCorrection(textureLeft, "left");
            refreshSinglePreviewCorrection(textureRight, "right");
        });
    }

    private void refreshSinglePreviewCorrection(AutoFitTextureView textureView, String cameraKey) {
        if (textureView == null) return;
        textureView.post(() -> {
            int viewWidth = textureView.getWidth();
            int viewHeight = textureView.getHeight();
            if (viewWidth <= 0 || viewHeight <= 0) return;

            android.graphics.Matrix base = previewBaseTransforms.get(cameraKey);
            android.graphics.Matrix matrix;
            if (base != null) {
                matrix = new android.graphics.Matrix(base);
            } else {
                matrix = new android.graphics.Matrix(); // identity
            }
            PreviewCorrection.postApply(matrix, appConfig, cameraKey, viewWidth, viewHeight);
            textureView.setTransform(matrix);
        });
    }

    /**
     * 显示预览画面矫正悬浮窗
     */
    public void showPreviewCorrectionFloating() {
        if (previewCorrectionFloatingWindow != null && previewCorrectionFloatingWindow.isShowing()) {
            return;
        }
        previewCorrectionFloatingWindow = new PreviewCorrectionFloatingWindow(this);
        previewCorrectionFloatingWindow.show();
    }

    /**
     * Закрыто预览画面矫正悬浮窗
     */
    public void dismissPreviewCorrectionFloating() {
        if (previewCorrectionFloatingWindow != null) {
            previewCorrectionFloatingWindow.dismiss();
            previewCorrectionFloatingWindow = null;
        }
    }

    // ==================== 鱼眼矫正 ====================

    /**
     * 鱼眼矫正ВклВыкл切换后Обновить所有Камера预览
     * необходимо重建 Camera session（切换直接 Surface / GL 间层)
     */
    public void refreshFisheyeCorrection() {
        MultiCameraManager cm = cameraManager;
        if (cm == null) return;
        String[] positions = {"front", "back", "left", "right"};
        for (String pos : positions) {
            com.kooo.evcam.camera.SingleCamera camera = cm.getCamera(pos);
            if (camera != null) {
                camera.recreateForFisheyeToggle();
            }
        }
    }

    /**
     * 显示鱼眼矫正悬浮窗
     */
    public void showFisheyeCorrectionFloating() {
        if (fisheyeCorrectionFloatingWindow != null && fisheyeCorrectionFloatingWindow.isShowing()) {
            return;
        }
        fisheyeCorrectionFloatingWindow = new FisheyeCorrectionFloatingWindow(this);
        fisheyeCorrectionFloatingWindow.show();
    }

    /**
     * Закрыто鱼眼矫正悬浮窗
     */
    public void dismissFisheyeCorrectionFloating() {
        if (fisheyeCorrectionFloatingWindow != null) {
            fisheyeCorrectionFloatingWindow.dismiss();
            fisheyeCorrectionFloatingWindow = null;
        }
    }

    // ==================== отладкаИнформация覆盖层（连点5显示) ====================

    /**
     *  Запись布局检测连续5 раз点击，切换отладкаИнформация显示
     */
    private void initDebugOverlayTapDetection() {
        if (recordingLayout == null) return;
        recordingLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                long now = System.currentTimeMillis();
                if (now - debugLastTapTime > DEBUG_TAP_INTERVAL_MS) {
                    debugTapCount = 0;
                }
                debugTapCount++;
                debugLastTapTime = now;
                if (debugTapCount >= DEBUG_TAP_COUNT) {
                    debugTapCount = 0;
                    toggleDebugOverlay();
                }
            }
            return false; // 不消费事件，让Другое点击/触摸нормально工作
        });
    }

    private void toggleDebugOverlay() {
        debugOverlayVisible = !debugOverlayVisible;
        if (debugOverlayVisible) {
            tvDebugOverlay.setVisibility(View.VISIBLE);
            startDebugUpdates();
            android.widget.Toast.makeText(this, "Отладочная информация включена", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            tvDebugOverlay.setVisibility(View.GONE);
            stopDebugUpdates();
            android.widget.Toast.makeText(this, "Отладочная информация выключена", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void startDebugUpdates() {
        stopDebugUpdates();
        debugUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!debugOverlayVisible) return;
                updateDebugInfo();
                debugUpdateHandler.postDelayed(this, 1000);
            }
        };
        debugUpdateHandler.post(debugUpdateRunnable);
    }

    private void stopDebugUpdates() {
        if (debugUpdateRunnable != null) {
            debugUpdateHandler.removeCallbacks(debugUpdateRunnable);
            debugUpdateRunnable = null;
        }
    }

    private void updateDebugInfo() {
        if (tvDebugOverlay == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("── EVCam Debug ──\n");

        // Камера FPS  и Разрешение
        if (cameraManager != null) {
            sb.append(cameraManager.getDebugStats());
        } else {
            sb.append("Camera: not initialized");
        }

        // ЗаписьСтатус
        sb.append("\n\n");
        sb.append("Запись: ").append(isRecording ? "● REC" : "○ Стоп");
        if (isRecording) {
            sb.append("  Режим: ").append(appConfig.getRecordingMode());
        }

        // 内存использование
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMB = rt.maxMemory() / (1024 * 1024);
        sb.append("\n");
        sb.append("Память: ").append(usedMB).append("/").append(totalMB).append(" MB");

        // 车型
        sb.append("\n");
        sb.append("Модель: ").append(appConfig.getCarModel());
        sb.append("  Камеры: ").append(appConfig.getCameraCount());

        // 版本
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            sb.append("\n");
            sb.append("Версия: ").append(versionName);
        } catch (Exception ignored) {}

        tvDebugOverlay.setText(sb.toString());
    }

    /**
     * проверка 否необходимо 主题切换后ВосстановлениеЗапись
     *  Камераинициализациязавершение后调用，Если доВыполняется Запись（非DingTalkкоманда)，则автоматическиВосстановлениеЗапись
     */
    private void checkResumeRecordingAfterRecreate() {
        if (!shouldResumeRecordingAfterRecreate) {
            return;
        }
        
        AppLog.d(TAG, "ОбнаруженонеобходимоВосстановлениеЗапись（主题切换后)，将 2 сек.后автоматическиВосстановление...");
        
        // 延迟2 сек.后ВосстановлениеЗапись，确保所有Камеравсе准备绪
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // 再 разпроверка 否经 Запись（可能用户вручнуюВкл始)
            if (isRecording) {
                AppLog.d(TAG, " Запись，跳过ВосстановлениеЗапись");
                shouldResumeRecordingAfterRecreate = false;
                return;
            }
            
            // проверкаКамера 否绪
            if (cameraManager == null || !cameraManager.hasConnectedCameras()) {
                AppLog.w(TAG, "КамераНе 绪，无法ВосстановлениеЗапись");
                Toast.makeText(this, "Камера не готова, не удалось возобновить запись", Toast.LENGTH_SHORT).show();
                shouldResumeRecordingAfterRecreate = false;
                savedRecordingStartTime = 0;
                savedSegmentCount = 1;
                return;
            }
            
            AppLog.d(TAG, "主题切换后автоматическиВосстановлениеЗапись...");
            startRecording();
            Toast.makeText(this, "Запись автоматически возобновлена", Toast.LENGTH_SHORT).show();
            // 注意：shouldResumeRecordingAfterRecreate  首 раз数据写入回调Сброс，
            // 以便计时器использованиеСохранить 时间
        }, 2000);  // 延迟2 сек.
    }
    
    /**
     * проверка并触发автоматическиЗапись
     *  Камераинициализациязавершение后调用，Если 用户Включить"ЗапускавтоматическиЗапись"则автоматическиНачать запись
     */
    private void checkAutoStartRecording() {
        // Если Выполняется ВосстановлениеЗапись（主题切换后)，跳过автоматическиЗапись
        if (shouldResumeRecordingAfterRecreate) {
            AppLog.d(TAG, "Выполняется ВосстановлениеЗапись，跳过автоматическиЗаписьпроверка");
            return;
        }
        
        // 避免重复触发
        if (autoStartRecordingTriggered) {
            AppLog.d(TAG, "автоматическиЗапись触发过，跳过");
            return;
        }
        
        // проверка 否ВключитьавтоматическиЗапись
        if (!appConfig.isAutoStartRecording()) {
            AppLog.d(TAG, "Не ВключитьЗапускавтоматическиЗапись");
            return;
        }
        
        // 标记触发
        autoStartRecordingTriggered = true;
        isAutoRecordingPending = true;  // 标记автоматическиЗаписьВыполняется ожидание（防止 onPause ЗакрытоКамера)
        AppLog.d(TAG, "ОбнаруженоВключитьЗапускавтоматическиЗапись，将 2 сек.后автоматическиНачать запись...");
        
        // 延迟2 сек.后Начать запись，确保所有Камеравсе准备绪
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // автоматическиЗаписьожиданиезавершить
            isAutoRecordingPending = false;
            
            // 再 разпроверка 否经 Запись（可能用户вручнуюВкл始)
            if (isRecording) {
                AppLog.d(TAG, " Запись，跳过автоматическиЗапись");
                return;
            }
            
            // проверкаКамера 否绪
            if (cameraManager == null || !cameraManager.hasConnectedCameras()) {
                AppLog.w(TAG, "КамераНе 绪，无法автоматическиНачать запись");
                Toast.makeText(this, "Камера не готова, автозапись не удалась", Toast.LENGTH_SHORT).show();
                return;
            }
            
            AppLog.d(TAG, "автоматическиНачать запись...");
            startRecording();
            Toast.makeText(this, "Автозапись запущена", Toast.LENGTH_SHORT).show();
        }, 2000);  // 延迟2 сек.
    }
    
    /**
     * ЗапускавтоматическиЗаписьПлановая проверка
     * 定期проверкаЗаписьСтатус，Если ВключитьавтоматическиЗапись且不 вручнуюОстановка ，则автоматическиВосстановлениеЗапись
     */
    private void startAutoRecordingCheck() {
        // проверка 否ВключитьавтоматическиЗапись
        if (!appConfig.isAutoStartRecording()) {
            AppLog.d(TAG, "Не ВключитьавтоматическиЗапись，跳过Плановая проверка");
            return;
        }
        
        // инициализация Handler
        if (autoRecordingCheckHandler == null) {
            autoRecordingCheckHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        
        // Отменадо проверказадача
        if (autoRecordingCheckRunnable != null) {
            autoRecordingCheckHandler.removeCallbacks(autoRecordingCheckRunnable);
        }
        
        // 创建Плановая проверказадача
        autoRecordingCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndRestoreAutoRecording();
                // продолжить一 разпроверка
                if (autoRecordingCheckHandler != null && autoRecordingCheckRunnable != null) {
                    autoRecordingCheckHandler.postDelayed(this, AUTO_RECORDING_CHECK_INTERVAL_MS);
                }
            }
        };
        
        // 延迟首 разпроверка（ автоматическиЗаписьЗапуск一些时间)
        autoRecordingCheckHandler.postDelayed(autoRecordingCheckRunnable, AUTO_RECORDING_CHECK_INTERVAL_MS);
        AppLog.d(TAG, "автоматическиЗаписьПлановая проверкаЗапущено（每 " + (AUTO_RECORDING_CHECK_INTERVAL_MS / 1000) + "  сек.проверка一 раз)");
    }
    
    /**
     * ОстановкаавтоматическиЗаписьПлановая проверка
     */
    private void stopAutoRecordingCheck() {
        if (autoRecordingCheckHandler != null && autoRecordingCheckRunnable != null) {
            autoRecordingCheckHandler.removeCallbacks(autoRecordingCheckRunnable);
            AppLog.d(TAG, "автоматическиЗаписьПлановая проверкаОстановлено");
        }
        autoRecordingCheckRunnable = null;
    }
    
    /**
     * проверка并ВосстановлениеавтоматическиЗапись
     * 条件：ВключитьавтоматическиЗапись + 不 вручнуюОстановка + Текущий没 Запись + КамераПодключено
     */
    private void checkAndRestoreAutoRecording() {
        // проверка 否ВключитьавтоматическиЗапись
        if (!appConfig.isAutoStartRecording()) {
            return;
        }
        
        // Если 用户вручнуюОстановкаЗапись，不автоматическиВосстановление
        if (isManuallyStoppedRecording) {
            // 每5 мин.打印一 раз д.志（避免 д.志刷屏)
            return;
        }
        
        // Если 经 Запись，不необходимоВосстановление
        if (isRecording) {
            return;
        }
        
        // Если Выполняется 准备Запись，不необходимоВосстановление
        if (isAutoRecordingPending || isPreparingRecording) {
            return;
        }
        
        // проверкаКамера 否绪
        if (cameraManager == null || !cameraManager.hasConnectedCameras()) {
            AppLog.w(TAG, "автоматическиЗаписьпроверка：КамераНе 绪，跳过Восстановление");
            return;
        }
        
        // 满足所有条件，автоматическиВосстановлениеЗапись
        AppLog.d(TAG, "автоматическиЗаписьпроверка：ОбнаруженоНе  Запись，автоматическиВосстановлениеЗапись...");
        startRecording();
        Toast.makeText(this, "Запись автоматически возобновлена", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * инициализация息屏Статус广播接收器
     * 用于检测屏幕ВклВыклСтатус，实现息屏Записьфункция
     */
    private void initScreenStateReceiver() {
        screenStateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        screenStateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                
                if (android.content.Intent.ACTION_SCREEN_OFF.equals(action)) {
                    onScreenOff();
                } else if (android.content.Intent.ACTION_SCREEN_ON.equals(action)) {
                    onScreenOn();
                }
            }
        };
        
        // 注册广播接收器
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(android.content.Intent.ACTION_SCREEN_OFF);
        filter.addAction(android.content.Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        
        AppLog.d(TAG, "息屏Статус广播接收器注册");
        
        // инициализацияФоновый режим切换广播接收器
        initBackgroundCommandReceiver();
    }
    
    /**
     * инициализацияФоновый режим切换广播接收器
     * 用于接收Удалённый"фон"команда，避免использование startActivity 导致闪屏
     */
    private void initBackgroundCommandReceiver() {
        backgroundCommandReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                String action = intent.getAction();
                if (WakeUpHelper.ACTION_MOVE_TO_BACKGROUND.equals(action)) {
                    AppLog.d(TAG, "Получена команда: Фоновый режим切换广播");
                    // 直接退 до Фоновый режим，无需Запуск Activity
                    moveTaskToBack(true);
                    AppLog.d(TAG, "Приложение переключено в фоновый режим（通过广播)");
                }
            }
        };
        
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(WakeUpHelper.ACTION_MOVE_TO_BACKGROUND);
        registerReceiver(backgroundCommandReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        
        AppLog.d(TAG, "Фоновый режим切换广播接收器注册");
    }
    
    /**
     * 息屏时 处理逻辑
     */
    private void onScreenOff() {
        isScreenOff = true;
        AppLog.d(TAG, "Обнаружено息屏");
        
        // Уведомление心跳управление器屏幕Статус（由 HeartbeatManager 处理息屏推图逻辑)
        if (heartbeatManager != null) {
            heartbeatManager.onScreenOff();
        }
        
        // Отмена可能существует 亮屏ВосстановлениеЗаписьзадача
        if (screenOnStartRunnable != null) {
            screenStateHandler.removeCallbacks(screenOnStartRunnable);
            screenOnStartRunnable = null;
        }
        
        // 判断 否为"автоматическиЗапись+息屏Запись" групп合（необходимо保持相机活跃)
        boolean keepCameraActive = appConfig.isAutoStartRecording() && appConfig.isScreenOffRecordingEnabled();
        
        // Если Выполняется Запись
        if (isRecording) {
            // Если Вкл启автоматическиЗапись+息屏Запись，продолжитьЗапись
            if (keepCameraActive) {
                AppLog.d(TAG, "息屏ЗаписьВключено，продолжитьЗапись");
                return;
            }
            
            // Если Не Вкл启автоматическиЗаписьфункция，不干预вручнуюЗапись，также不退Фоновый режим
            if (!appConfig.isAutoStartRecording()) {
                AppLog.d(TAG, "вручнуюЗапись，不受息屏影响，保持Передний план");
                return;
            }
            
            // Вкл启автоматическиЗапись但Не Вкл启息屏Запись，10 сек.后Остановить запись，15 сек.后退Фоновый режим
            AppLog.d(TAG, "息屏ЗаписьНе Включить，将 10 сек.后Остановить запись，15 сек.后退Фоновый режим...");
            wasRecordingBeforeScreenOff = true;
            
            screenOffStopRunnable = () -> {
                // 再 разпроверка 否仍然息屏
                if (!isScreenOff) {
                    AppLog.d(TAG, "屏幕亮起，ОтменаОстановить запись");
                    return;
                }
                
                // проверка 否仍 Запись
                if (!isRecording) {
                    AppLog.d(TAG, "不 ЗаписьСтатус，无需Остановка");
                    return;
                }
                
                // проверка 否ВключитьавтоматическиЗапись（防止 ожидание期间用户ЗакрытоНастройки)
                if (!appConfig.isAutoStartRecording()) {
                    AppLog.d(TAG, "автоматическиЗаписьфункцияЗакрыто，忽略");
                    return;
                }
                
                // проверка息屏ЗаписьНастройки 否 更改（防止 ожидание期间用户Вкл启息屏Запись)
                if (appConfig.isScreenOffRecordingEnabled()) {
                    AppLog.d(TAG, "息屏Запись Включить，продолжитьЗапись");
                    return;
                }
                
                AppLog.d(TAG, "息屏持续10 сек.，автоматическиОстановить запись");
                stopRecording();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Экран выключен 10 сек, запись остановлена", Toast.LENGTH_SHORT).show();
                });
            };
            
            screenStateHandler.postDelayed(screenOffStopRunnable, SCREEN_OFF_DELAY_MS);
            
            // 同时安排15 сек.后退Фоновый режим（ и Остановить записьзадача并行)
            scheduleBackgroundTask();
        } else {
            // Не  Запись
            if (keepCameraActive) {
                // Вкл启автоматическиЗапись+息屏Запись，保持Передний план（以便亮屏后可以立т.е.Запись)
                AppLog.d(TAG, "息屏Записьрежим，保持相机活跃");
                return;
            }
            
            // Другое情况：15 сек.后退Фоновый режим，释放相机资源
            AppLog.d(TAG, "Не  Запись，将 15 сек.后退 до Фоновый режим释放相机资源...");
            scheduleBackgroundTask();
        }
    }
    
    /**
     * 安排息屏后退 до Фоновый режим задача
     */
    private void scheduleBackgroundTask() {
        // Отмена可能существует 退Фоновый режимзадача
        if (screenOffBackgroundRunnable != null) {
            screenStateHandler.removeCallbacks(screenOffBackgroundRunnable);
        }
        
        screenOffBackgroundRunnable = () -> {
            // 再 разпроверка 否仍然息屏
            if (!isScreenOff) {
                AppLog.d(TAG, "屏幕亮起，Отмена退Фоновый режим");
                return;
            }
            
            // Если Выполняется Запись，不退Фоновый режим
            if (isRecording) {
                AppLog.d(TAG, "Выполняется Запись，不退Фоновый режим");
                return;
            }
            
            // Если Вкл启автоматическиЗапись+息屏Запись，不退Фоновый режим
            if (appConfig.isAutoStartRecording() && appConfig.isScreenOffRecordingEnabled()) {
                AppLog.d(TAG, "息屏ЗаписьрежимВключено，不退Фоновый режим");
                return;
            }
            
            AppLog.d(TAG, "息屏持续15 сек.，退 до Фоновый режим释放相机资源");
            
            // ЗакрытоКамера释放资源
            if (cameraManager != null) {
                cameraManager.closeAllCameras();
                AppLog.d(TAG, "Закрыто所有Камера");
            }
            
            // 退 до Фоновый режим
            moveTaskToBack(true);
            
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Экран выключен 15 сек, переход в фон", Toast.LENGTH_SHORT).show();
            });
        };
        
        screenStateHandler.postDelayed(screenOffBackgroundRunnable, SCREEN_OFF_BACKGROUND_DELAY_MS);
    }
    
    /**
     * 亮屏时 处理逻辑
     */
    private void onScreenOn() {
        isScreenOff = false;
        AppLog.d(TAG, "Обнаружено亮屏");
        
        // Уведомление心跳управление器屏幕Статус（由 HeartbeatManager 处理Остановка息屏推图)
        if (heartbeatManager != null) {
            heartbeatManager.onScreenOn();
        }
        
        // Отмена可能существует 息屏Остановить записьзадача
        if (screenOffStopRunnable != null) {
            screenStateHandler.removeCallbacks(screenOffStopRunnable);
            screenOffStopRunnable = null;
            // Если 仍 Запись，说明息屏Остановказадача没有выполнение，Сброс标记
            if (isRecording) {
                AppLog.d(TAG, "息屏期间ЗаписьНе  Остановка（亮屏及时)，Сброс标记");
                wasRecordingBeforeScreenOff = false;
            }
        }
        
        // Отмена可能существует 退Фоновый режимзадача
        if (screenOffBackgroundRunnable != null) {
            screenStateHandler.removeCallbacks(screenOffBackgroundRunnable);
            screenOffBackgroundRunnable = null;
            AppLog.d(TAG, "亮屏，Отмена退Фоновый режимзадача");
        }
        
        // проверка 否ВключитьавтоматическиЗаписьфункция
        if (!appConfig.isAutoStartRecording()) {
            AppLog.d(TAG, "Не ВключитьавтоматическиЗаписьфункция，忽略亮屏事件");
            return;
        }
        
        // проверка息屏ЗаписьНастройки
        if (appConfig.isScreenOffRecordingEnabled()) {
            // 息屏ЗаписьВключено，无需Восстановление（一直 Запись)
            AppLog.d(TAG, "息屏ЗаписьВключено，无需ВосстановлениеЗапись");
            return;
        }
        
        // проверка 否необходимоВосстановлениеЗапись（до因息屏而ОстановкаЗапись)
        if (!wasRecordingBeforeScreenOff) {
            AppLog.d(TAG, "息屏前Не  ЗаписьилиЗаписьНе  断，无需Восстановление");
            return;
        }
        
        // Если 经 Запись，无需Восстановление（这种情况理论不会发生，因为面经处理)
        if (isRecording) {
            AppLog.d(TAG, " Запись，无需Восстановление");
            wasRecordingBeforeScreenOff = false;
            return;
        }
        
        AppLog.d(TAG, "亮屏后将 10 сек.后ВосстановлениеЗапись...");
        
        // Если КамераЗакрыто，先重新открыть
        if (cameraManager != null && !cameraManager.hasConnectedCameras()) {
            AppLog.d(TAG, "КамераЗакрыто，先重新открытьКамера");
            try {
                cameraManager.openAllCameras();
            } catch (Exception e) {
                AppLog.e(TAG, "重新открытьКамераОшибка: " + e.getMessage(), e);
            }
        }
        
        screenOnStartRunnable = () -> {
            // 再 разпроверка 否仍然亮屏
            if (isScreenOff) {
                AppLog.d(TAG, "屏幕又息屏，ОтменаВосстановлениеЗапись");
                return;
            }
            
            // Сброс标记
            wasRecordingBeforeScreenOff = false;
            
            // проверка 否ВключитьавтоматическиЗапись（防止 ожидание期间用户ЗакрытоНастройки)
            if (!appConfig.isAutoStartRecording()) {
                AppLog.d(TAG, "автоматическиЗаписьфункцияЗакрыто，不ВосстановлениеЗапись");
                return;
            }
            
            // проверка息屏ЗаписьНастройки
            if (appConfig.isScreenOffRecordingEnabled()) {
                AppLog.d(TAG, "息屏Запись Включить，无需处理");
                return;
            }
            
            // проверка 否 Запись
            if (isRecording) {
                AppLog.d(TAG, " Запись，无需Восстановление");
                return;
            }
            
            // проверкаКамера 否绪
            if (cameraManager == null || !cameraManager.hasConnectedCameras()) {
                AppLog.w(TAG, "КамераНе 绪，попытка重新открыть...");
                // 再 разпопыткаоткрытьКамера
                if (cameraManager != null) {
                    try {
                        cameraManager.openAllCameras();
                        // 延迟2 сек.后再 разпопыткаВосстановлениеЗапись
                        screenStateHandler.postDelayed(() -> {
                            if (!isScreenOff && !isRecording && cameraManager.hasConnectedCameras()) {
                                AppLog.d(TAG, "Камера绪，Вкл始ВосстановлениеЗапись");
                                startRecording();
                                Toast.makeText(MainActivity.this, "Запись автоматически возобновлена", Toast.LENGTH_SHORT).show();
                            }
                        }, 2000);
                    } catch (Exception e) {
                        AppLog.e(TAG, "открытьКамераОшибка: " + e.getMessage(), e);
                    }
                }
                return;
            }
            
            AppLog.d(TAG, "亮屏持续10 сек.，автоматическиВосстановлениеЗапись");
            startRecording();
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Экран включён 10 сек, запись возобновлена", Toast.LENGTH_SHORT).show();
            });
        };
        
        screenStateHandler.postDelayed(screenOnStartRunnable, SCREEN_ON_DELAY_MS);
    }
    /**
     * 切换ЗаписьСтатус（Вкл始/Остановка)
     */
    private void toggleRecording() {
        // 防双击保护
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRecordButtonClickTime < RECORD_BUTTON_CLICK_INTERVAL) {
            AppLog.d(TAG, "Запись按钮点击过快，忽略（间隔: " + (currentTime - lastRecordButtonClickTime) + "ms)");
            return;
        }
        lastRecordButtonClickTime = currentTime;
        
        if (isRecording) {
            // 用户вручнуюОстановить запись，НастройкивручнуюОстановка标记
            // 这样автоматическиЗаписьпроверка不会автоматическиВосстановлениеЗапись
            isManuallyStoppedRecording = true;
            AppLog.d(TAG, "用户вручнуюОстановить запись，автоматическиЗаписьпроверка将不再автоматическиВосстановление");
            
            // 用户вручнуюОстановить запись，Сброс息屏Запись标记
            // 这样亮屏后不会Ошибка地ВосстановлениеЗапись
            wasRecordingBeforeScreenOff = false;
            stopRecording();
        } else {
            // 用户вручнуюНачать запись，СбросвручнуюОстановка标记
            // 这样后续Если ЗаписьаномалияОстановка，可以автоматическиВосстановление
            isManuallyStoppedRecording = false;
            AppLog.d(TAG, "用户вручнуюНачать запись，автоматическиЗаписьпроверкаВключено");
            startRecording();
        }
    }

    private void startRecording() {
        if (cameraManager != null && !cameraManager.isRecording()) {
            //  от конфигурация读取Включить ЗаписьКамера
            AppConfig appConfig = new AppConfig(this);
            java.util.Set<String> enabledCameras = appConfig.getEnabledRecordingCameras();
            
            if (enabledCameras.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одну камеру для записи", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 检测USB-накопитель回退情况（用户ВыбратьUSB-накопитель但不Доступно)
            boolean isFallback = StorageHelper.isSdCardFallback(this);
            
            // 生成统一时间戳
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            
            // использование指定 Камера进行Запись
            boolean success = cameraManager.startRecording(timestamp, enabledCameras);
            if (success) {
                isRecording = true;
                isPreparingRecording = true;  // 标记为准备Статус

                // ЗапускПередний планСервис保护（防止Фоновый режимЗапись 断)
                CameraForegroundService.start(this, "Запись видео", "Идёт запись, нажмите для возврата");

                // 显示准备指示器（橙色Поворот 圈)
                // 首 раз数据写入后会автоматически切换 до 绿色闪烁动画
                showPreparingIndicator();
                
                // 注意：Запись计时器延迟 до 首 раз写入回调Запуск
                // 这样计时 от "действуетЗапись"Вкл始，而不  от "попыткаЗапись"Вкл始

                // ОтправкаЗаписьСтатус广播（Уведомление悬浮窗)
                FloatingWindowService.sendRecordingStateChanged(this, true);

                // L7-Мульти-кнопки布局：обновлениеЗапись按钮文字为"Стоп"
                if (AppConfig.CAR_MODEL_L7_MULTI.equals(appConfig.getCarModel()) && btnStartRecord != null) {
                    btnStartRecord.setText("Стоп");
                }

                // 显示Уведомление：优先显示回退Уведомление（每 раз冷Запуск只显示一 раз)
                if (isFallback && !AppConfig.isSdFallbackShownThisSession()) {
                    AppConfig.setSdFallbackShownThisSession(true);
                    Toast.makeText(this, "USB не обнаружен, используется внутреннее хранилище", Toast.LENGTH_LONG).show();
                    AppLog.w(TAG, "USB-накопитель回退：用户ВыбратьUSB-накопитель但不Доступно，использованиеВнутренняя память");
                } else {
                    // 显示Запись Камера数量
                    int cameraCount = enabledCameras.size();
                    String cameraText = cameraCount == appConfig.getCameraCount() ? "Все" : cameraCount + " шт.";
                    Toast.makeText(this, "Начало записи " + cameraText + " камер(ы)", Toast.LENGTH_SHORT).show();
                }
                AppLog.d(TAG, "Recording started with " + enabledCameras.size() + " camera(s): " + enabledCameras);
            } else {
                Toast.makeText(this, "Ошибка записи", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopRecording() {
        if (cameraManager != null) {
            cameraManager.stopRecording();
            isRecording = false;
            isPreparingRecording = false;  // Сброс准备Статус

            // ОстановкаПередний планСервис
            CameraForegroundService.stop(this);

            // Остановка闪烁动画，Восстановление红色
            stopBlinkAnimation();
            
            // Остановить запись计时器
            stopRecordingTimer();

            // ОтправкаЗаписьСтатус广播（Уведомление悬浮窗)
            FloatingWindowService.sendRecordingStateChanged(this, false);

            // L7-Мульти-кнопки布局：ВосстановлениеЗапись按钮文字为"Запись"
            if (AppConfig.CAR_MODEL_L7_MULTI.equals(appConfig.getCarModel()) && btnStartRecord != null) {
                btnStartRecord.setText("Запись");
            }

            Toast.makeText(this, "Запись остановлена", Toast.LENGTH_SHORT).show();
            AppLog.d(TAG, "Recording stopped, foreground service stopped");
        }
    }

    /**
     * 完全Выход из приложения（包括Фоновый режим进程)
     * 这 用户主动Выход，необходимоОстановка所有Сервис
     */
    private void exitApp() {
        AppLog.d(TAG, "用户求Выход из приложения，Остановка所有Сервис...");
        
        // Остановить запись（Если Выполняется Запись)
        if (isRecording) {
            stopRecording();
        }

        // ОстановкаПередний планСервис（确保Очистка )
        CameraForegroundService.stop(this);

        // Остановка所有УдалённыйСервис（DingTalk + Telegram)
        // 通过 RemoteServiceManager 统一управление
        RemoteServiceManager.getInstance().stopAllServices();
        dingTalkStreamManager = null;
        dingTalkApiClient = null;
        telegramBotManager = null;
        telegramApiClient = null;

        // 释放悬浮窗Сервис
        FloatingWindowService.stop(this);
        
        // 释放持续唤醒锁
        WakeUpHelper.releasePersistentWakeLock();

        // 释放Камера资源
        if (cameraManager != null) {
            cameraManager.release();
        }
        com.kooo.evcam.camera.CameraManagerHolder.getInstance().release();
        
        // Сохранить д.志（System.exit 会跳过 onDestroy，所以这里вручнуюСохранить)
        AppLog.saveToPersistentLog(this);

        // завершить所有Activity并Выход из приложения
        finishAffinity();

        // 完全Выход进程
        System.exit(0);
    }

    private void startBlinkAnimation() {
        if (blinkHandler == null) {
            blinkHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }

        isBlinking = true;
        blinkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBlinking) {
                    // 切换颜色：绿色 и 深绿色交替
                    int currentColor = btnStartRecord.getTextColors().getDefaultColor();
                    if (currentColor == 0xFF00FF00) {  // 亮绿色
                        btnStartRecord.setTextColor(0xFF006400);  // 深绿色
                    } else {
                        btnStartRecord.setTextColor(0xFF00FF00);  // 亮绿色
                    }
                    blinkHandler.postDelayed(this, 1000);  // 每500ms闪烁一 раз
                }
            }
        };

        // 初始Настройки为绿色
        btnStartRecord.setTextColor(0xFF00FF00);
        blinkHandler.post(blinkRunnable);
    }

    private void stopBlinkAnimation() {
        isBlinking = false;
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
        }
        // Восстановление红色（确保 主线程выполнение，且按钮不пусто)
        if (btnStartRecord != null) {
            runOnUiThread(() -> {
                if (btnStartRecord != null) {
                    btnStartRecord.setTextColor(0xFFFF0000);
                }
            });
        }
    }

    /**
     * 显示准备Статус
     * 按钮变为暗绿色（不闪烁)，表示ЗаписьВыполняется инициализация
     */
    private void showPreparingIndicator() {
        if (btnStartRecord != null) {
            // Настройки按钮为暗绿色（不闪烁)，表示准备
            btnStartRecord.setTextColor(0xFF006400);  // 暗绿色
            AppLog.d(TAG, "进入准备Статус：暗绿色（不闪烁)");
        }
    }

    /**
     * завершить准备Статус
     * Запись真正Вкл始后调用，Вкл始绿色闪烁动画
     */
    private void hidePreparingIndicator() {
        // Вкл始绿色闪烁动画（Если Выполняется Запись)
        if (isRecording || isRemoteRecording) {
            startBlinkAnimation();
            AppLog.d(TAG, "准备завершение，Вкл始绿色闪烁");
        }
    }

    private void takePicture() {
        if (cameraManager != null) {
            cameraManager.takePicture();
            Toast.makeText(this, "Фото сделано", Toast.LENGTH_SHORT).show();
            AppLog.d(TAG, "Picture taken");
        }
    }

    /**
     * Если  Удалённый唤醒 ，завершение后автоматически退回Фоновый режим
     * 延迟2 сек.后выполнение，让用户看 до 传Успешно Уведомление
     */
    private void returnToBackgroundIfRemoteWakeUp() {
        if (!isRemoteWakeUp) {
            AppLog.d(TAG, "Not a remote wake-up, staying in foreground");
            return;
        }

        AppLog.d(TAG, "Remote command completed, will return to background in 2 seconds");

        // 延迟2 сек.后退回Фоновый режим，让用户看 до  Toast Уведомление
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Сброс标记
            isRemoteWakeUp = false;

            // 释放唤醒锁，让屏幕可以自然熄灭
            WakeUpHelper.releaseWakeLock();

            // 将Приложение退 до Фоновый режим
            AppLog.d(TAG, "Moving task to back...");
            moveTaskToBack(true);

            AppLog.d(TAG, "Returned to background successfully");
        }, 2000);
    }

    /**
     * ЗапускУдалённыйПросмотрСервис
     */
    public void startDingTalkService() {
        if (!dingTalkConfig.isConfigured()) {
            Toast.makeText(this, "Сначала настройте параметры DingTalk", Toast.LENGTH_SHORT).show();
            return;
        }

        // проверка本地实例
        if (dingTalkStreamManager != null && dingTalkStreamManager.isRunning()) {
            AppLog.d(TAG, "УдалённыйПросмотрСервис Работа（本地实例)");
            return;
        }
        
        // проверка RemoteServiceManager  否有实例（防止竞态条件)
        if (RemoteServiceManager.getInstance().isDingTalkStartingOrRunning()) {
            AppLog.d(TAG, "УдалённыйПросмотрСервис Работа（RemoteServiceManager)，Получение有实例");
            dingTalkApiClient = RemoteServiceManager.getInstance().getDingTalkApiClient();
            dingTalkStreamManager = RemoteServiceManager.getInstance().getDingTalkStreamManager();
            updateRemoteViewFragmentUI();
            return;
        }

        AppLog.d(TAG, "Выполняется ЗапускУдалённыйПросмотрСервис...");

        // 创建 API 客户端
        dingTalkApiClient = new DingTalkApiClient(dingTalkConfig);

        // 创建Подключение回调
        DingTalkStreamManager.ConnectionCallback connectionCallback = new DingTalkStreamManager.ConnectionCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    AppLog.d(TAG, "УдалённыйПросмотрСервисПодключено");
                    Toast.makeText(MainActivity.this, "Удалённый сервис DingTalk запущен", Toast.LENGTH_SHORT).show();
                    // Уведомление RemoteViewFragment обновление UI
                    updateRemoteViewFragmentUI();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    AppLog.d(TAG, "УдалённыйПросмотрСервисотключено");
                    // Уведомление RemoteViewFragment обновление UI
                    updateRemoteViewFragmentUI();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    AppLog.e(TAG, "УдалённыйПросмотрСервисОшибка подключения: " + error);
                    Toast.makeText(MainActivity.this, "Ошибка подключения: " + error, Toast.LENGTH_LONG).show();
                    // Уведомление RemoteViewFragment обновление UI
                    updateRemoteViewFragmentUI();
                });
            }
        };

        // обновлениеУдалённыйкоманда分发器  API 客户端
        if (remoteCommandDispatcher != null) {
            remoteCommandDispatcher.setDingTalkApiClient(dingTalkApiClient);
        }

        // 创建команда回调（использованиеУдалённыйкоманда分发器)
        DingTalkStreamManager.CommandCallback commandCallback = new DingTalkStreamManager.CommandCallback() {
            @Override
            public void onRecordCommand(String conversationId, String conversationType, String userId, int durationSeconds) {
                // использование分发器处理Удалённая запись
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.startDingTalkRecording(conversationId, conversationType, userId, durationSeconds);
                }
            }

            @Override
            public void onPhotoCommand(String conversationId, String conversationType, String userId) {
                // использование分发器处理УдалённыйФото
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.startDingTalkPhoto(conversationId, conversationType, userId);
                }
            }

            @Override
            public String getStatusInfo() {
                return buildStatusInfo();
            }

            @Override
            public String onStartRecordingCommand() {
                return handleStartRecordingCommand();
            }

            @Override
            public String onStopRecordingCommand() {
                return handleStopRecordingCommand();
            }

            @Override
            public String onExitCommand(boolean confirmed) {
                return handleExitCommand(confirmed);
            }

            @Override
            public String onForegroundCommand() {
                return handleForegroundCommand();
            }

            @Override
            public String onBackgroundCommand() {
                return handleBackgroundCommand();
            }
        };

        // 创建并Запуск Stream управление器（Включитьавтоматически重连)
        dingTalkStreamManager = new DingTalkStreamManager(this, dingTalkConfig, dingTalkApiClient, connectionCallback);
        dingTalkStreamManager.start(commandCallback, true); // Включитьавтоматически重连
        
        // 注册 до  RemoteServiceManager（确保 Activity  回收后Сервис仍可Работа)
        RemoteServiceManager.getInstance().setDingTalkService(dingTalkStreamManager, dingTalkApiClient);
    }

    /**
     * ОстановкаУдалённыйПросмотрСервис
     */
    public void stopDingTalkService() {
        if (dingTalkStreamManager != null) {
            AppLog.d(TAG, "Выполняется ОстановкаУдалённыйПросмотрСервис...");
            dingTalkStreamManager.stop();
            dingTalkStreamManager = null;
            dingTalkApiClient = null;
            
            //  от  RemoteServiceManager очистка
            RemoteServiceManager.getInstance().clearDingTalkService();
            
            Toast.makeText(this, "Удалённый сервис остановлен", Toast.LENGTH_SHORT).show();
            // Уведомление RemoteViewFragment обновление UI
            updateRemoteViewFragmentUI();
        }
    }

    /**
     * ПолучениеУдалённыйПросмотрСервисРаботаСтатус
     */
    public boolean isDingTalkServiceRunning() {
        return dingTalkStreamManager != null && dingTalkStreamManager.isRunning();
    }

    // ==================== Telegram Сервисуправление ====================

    /**
     * Запуск Telegram УдалённыйСервис
     */
    public void startTelegramService() {
        if (!telegramConfig.isConfigured()) {
            Toast.makeText(this, "Сначала настройте Telegram Bot Token", Toast.LENGTH_SHORT).show();
            return;
        }

        // проверка本地实例
        if (telegramBotManager != null && telegramBotManager.isRunning()) {
            AppLog.d(TAG, "Telegram Сервис Работа（本地实例)");
            return;
        }
        
        // проверка RemoteServiceManager  否有实例（防止竞态条件)
        if (RemoteServiceManager.getInstance().isTelegramStartingOrRunning()) {
            AppLog.d(TAG, "Telegram Сервис Работа（RemoteServiceManager)，Получение有实例");
            telegramApiClient = RemoteServiceManager.getInstance().getTelegramApiClient();
            telegramBotManager = RemoteServiceManager.getInstance().getTelegramBotManager();
            updateTelegramFragmentUI();
            return;
        }

        AppLog.d(TAG, "Выполняется Запуск Telegram Сервис...");

        // 创建 API 客户端
        telegramApiClient = new TelegramApiClient(telegramConfig);

        // обновлениеУдалённыйкоманда分发器  API 客户端
        if (remoteCommandDispatcher != null) {
            remoteCommandDispatcher.setTelegramApiClient(telegramApiClient);
        }

        // 创建Подключение回调
        TelegramBotManager.ConnectionCallback connectionCallback = new TelegramBotManager.ConnectionCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    AppLog.d(TAG, "Telegram СервисПодключено");
                    Toast.makeText(MainActivity.this, "Telegram подключён", Toast.LENGTH_SHORT).show();
                    updateTelegramFragmentUI();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    AppLog.d(TAG, "Telegram Сервисотключено");
                    updateTelegramFragmentUI();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    AppLog.e(TAG, "Telegram СервисОшибка подключения: " + error);
                    Toast.makeText(MainActivity.this, "Ошибка подключения Telegram: " + error, Toast.LENGTH_LONG).show();
                    updateTelegramFragmentUI();
                });
            }
        };

        // 创建команда回调（использованиеУдалённыйкоманда分发器)
        TelegramBotManager.CommandCallback commandCallback = new TelegramBotManager.CommandCallback() {
            @Override
            public void onRecordCommand(long chatId, int durationSeconds) {
                pendingTelegramChatId = chatId;
                // использование分发器处理Удалённая запись
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.startTelegramRecording(chatId, durationSeconds);
                }
            }

            @Override
            public void onPhotoCommand(long chatId) {
                pendingTelegramChatId = chatId;
                // использование分发器处理УдалённыйФото
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.startTelegramPhoto(chatId);
                }
            }

            @Override
            public String getStatusInfo() {
                return buildStatusInfo();
            }

            @Override
            public String onStartRecordingCommand() {
                return handleStartRecordingCommand();
            }

            @Override
            public String onStopRecordingCommand() {
                return handleStopRecordingCommand();
            }

            @Override
            public String onExitCommand(boolean confirmed) {
                return handleExitCommand(confirmed);
            }

            @Override
            public String onForegroundCommand() {
                return handleForegroundCommand();
            }

            @Override
            public String onBackgroundCommand() {
                return handleBackgroundCommand();
            }
        };

        // 创建并Запуск Bot управление器
        telegramBotManager = new TelegramBotManager(this, telegramConfig, telegramApiClient, connectionCallback);
        telegramBotManager.start(commandCallback);
        
        // 注册 до  RemoteServiceManager（确保 Activity  回收后Сервис仍可Работа)
        RemoteServiceManager.getInstance().setTelegramService(telegramBotManager, telegramApiClient);
    }

    /**
     * Остановка Telegram УдалённыйСервис
     */
    public void stopTelegramService() {
        if (telegramBotManager != null) {
            AppLog.d(TAG, "Выполняется Остановка Telegram Сервис...");
            telegramBotManager.stop();
            telegramBotManager = null;
            telegramApiClient = null;
            
            //  от  RemoteServiceManager очистка
            RemoteServiceManager.getInstance().clearTelegramService();
            
            Toast.makeText(this, "Сервис Telegram остановлен", Toast.LENGTH_SHORT).show();
            updateTelegramFragmentUI();
        }
    }

    /**
     * Получение Telegram СервисРаботаСтатус
     */
    public boolean isTelegramServiceRunning() {
        return telegramBotManager != null && telegramBotManager.isRunning();
    }

    /**
     * обновление TelegramFragment   UI Статус
     */
    private void updateTelegramFragmentUI() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (fragment instanceof TelegramFragment) {
            ((TelegramFragment) fragment).updateServiceStatus();
        }
    }

    // ==================== FeishuСервисуправление ====================

    /**
     * ЗапускFeishuУдалённыйСервис
     */
    public void startFeishuService() {
        if (!feishuConfig.isConfigured()) {
            Toast.makeText(this, "Сначала настройте Feishu App ID и App Secret", Toast.LENGTH_SHORT).show();
            return;
        }

        // проверка本地实例
        if (feishuBotManager != null && feishuBotManager.isRunning()) {
            AppLog.d(TAG, "FeishuСервис Работа（本地实例)");
            return;
        }
        
        // проверка RemoteServiceManager  否有实例
        if (RemoteServiceManager.getInstance().isFeishuStartingOrRunning()) {
            AppLog.d(TAG, "FeishuСервис Работа（RemoteServiceManager)，Получение有实例");
            feishuApiClient = RemoteServiceManager.getInstance().getFeishuApiClient();
            feishuBotManager = RemoteServiceManager.getInstance().getFeishuBotManager();
            updateFeishuFragmentUI();
            return;
        }

        AppLog.d(TAG, "Выполняется ЗапускFeishuСервис...");

        // 创建 API 客户端
        feishuApiClient = new com.kooo.evcam.feishu.FeishuApiClient(feishuConfig);

        // обновлениеУдалённыйкоманда分发器  API 客户端
        if (remoteCommandDispatcher != null) {
            remoteCommandDispatcher.setFeishuApiClient(feishuApiClient);
        }

        // 创建Подключение回调
        com.kooo.evcam.feishu.FeishuBotManager.ConnectionCallback connectionCallback = 
            new com.kooo.evcam.feishu.FeishuBotManager.ConnectionCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    AppLog.d(TAG, "FeishuСервисПодключено");
                    Toast.makeText(MainActivity.this, "Feishu подключён", Toast.LENGTH_SHORT).show();
                    updateFeishuFragmentUI();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    AppLog.d(TAG, "FeishuСервисотключено");
                    updateFeishuFragmentUI();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    AppLog.e(TAG, "FeishuСервисОшибка подключения: " + error);
                    Toast.makeText(MainActivity.this, "Ошибка подключения Feishu: " + error, Toast.LENGTH_LONG).show();
                    updateFeishuFragmentUI();
                });
            }
        };

        // 创建команда回调（использованиеУдалённыйкоманда分发器)
        com.kooo.evcam.feishu.FeishuBotManager.CommandCallback commandCallback = 
            new com.kooo.evcam.feishu.FeishuBotManager.CommandCallback() {
            @Override
            public void onRecordCommand(String chatId, String messageId, int durationSeconds) {
                pendingFeishuChatId = chatId;
                // использование分发器处理Удалённая запись
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.startFeishuRecording(chatId, durationSeconds);
                }
            }

            @Override
            public void onPhotoCommand(String chatId, String messageId) {
                pendingFeishuChatId = chatId;
                // использование分发器处理УдалённыйФото
                if (remoteCommandDispatcher != null) {
                    remoteCommandDispatcher.startFeishuPhoto(chatId);
                }
            }

            @Override
            public String getStatusInfo() {
                return buildStatusInfo();
            }

            @Override
            public String onStartRecordingCommand() {
                return handleStartRecordingCommand();
            }

            @Override
            public String onStopRecordingCommand() {
                return handleStopRecordingCommand();
            }

            @Override
            public String onExitCommand(boolean confirmed) {
                return handleExitCommand(confirmed);
            }

            @Override
            public String onForegroundCommand() {
                return handleForegroundCommand();
            }

            @Override
            public String onBackgroundCommand() {
                return handleBackgroundCommand();
            }
        };

        // 创建并Запуск Bot управление器
        feishuBotManager = new com.kooo.evcam.feishu.FeishuBotManager(this, feishuConfig, feishuApiClient, connectionCallback);
        feishuBotManager.start(commandCallback);
        
        // 注册 до  RemoteServiceManager
        RemoteServiceManager.getInstance().setFeishuService(feishuBotManager, feishuApiClient);
    }

    /**
     * ОстановкаFeishuУдалённыйСервис
     */
    public void stopFeishuService() {
        if (feishuBotManager != null) {
            AppLog.d(TAG, "Выполняется ОстановкаFeishuСервис...");
            feishuBotManager.stop();
            feishuBotManager = null;
            feishuApiClient = null;
            
            //  от  RemoteServiceManager очистка
            RemoteServiceManager.getInstance().clearFeishuService();
            
            Toast.makeText(this, "Сервис Feishu остановлен", Toast.LENGTH_SHORT).show();
            updateFeishuFragmentUI();
        }
    }

    /**
     * ПолучениеFeishuСервисРаботаСтатус
     */
    public boolean isFeishuServiceRunning() {
        return feishuBotManager != null && feishuBotManager.isRunning();
    }

    /**
     * обновление FeishuFragment   UI Статус
     */
    private void updateFeishuFragmentUI() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (fragment instanceof FeishuFragment) {
            ((FeishuFragment) fragment).updateServiceStatus();
        }
    }

    /**
     * 构建ПриложениеСтатусИнформация（用于УдалённыйСтатус查询)
     */
    private String buildStatusInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Статус EVCam\n");
        sb.append("━━━━━━━━━━━━━━\n");
        
        try {
            // ЗаписьСтатус
            if (isRecording) {
                sb.append("🎬 Запись: идёт");
                if (isRemoteRecording) {
                    sb.append(" (удалённо)");
                }
                sb.append("\n");
                
                // Запись时长
                if (recordingStartTime > 0) {
                    long elapsedMs = System.currentTimeMillis() - recordingStartTime;
                    long totalSeconds = elapsedMs / 1000;
                    long minutes = totalSeconds / 60;
                    long seconds = totalSeconds % 60;
                    sb.append("⏱️ Длительность: ").append(String.format("%02d:%02d", minutes, seconds));
                    sb.append(" / сегм.").append(currentSegmentCount).append("\n");
                }
            } else {
                sb.append("🎬 Запись: нет\n");
            }
            
            // КамераСтатус
            if (cameraManager != null) {
                int connectedCount = cameraManager.getConnectedCameraCount();
                int totalCount = appConfig.getCameraCount();
                sb.append("📷 Камеры: ").append(connectedCount).append("/").append(totalCount).append(" подключено\n");
            } else {
                sb.append("📷 Камеры: не инициализированы\n");
            }
            
            // ХранилищеИнформация（简短版)
            try {
                boolean useExternal = appConfig.isUsingExternalSdCard();
                java.io.File storageDir = useExternal ? 
                        StorageHelper.getExternalSdCardRoot(this) : 
                        android.os.Environment.getExternalStorageDirectory();
                if (storageDir != null && storageDir.exists()) {
                    long available = StorageHelper.getAvailableSpace(storageDir);
                    String availableStr = StorageHelper.formatSize(available);
                    sb.append("💾 Хранилище: ").append(useExternal ? "USB" : "Внутреннее");
                    sb.append("(осталось ").append(availableStr).append(")\n");
                }
            } catch (Exception e) {
                // 忽略ХранилищеПолучениеОшибка
            }
            
            // ПриложениеСтатус（基于 Activity 生命周期)
            // isInBackground   onPause() 时设为 true，onResume() 时设为 false
            // moveTaskToBack() 会触发 onPause()，所以这 шт.判断 准确 
            sb.append("📱 Приложение: ").append(isInBackground ? "фон" : "активно").append("\n");
            
            // 分隔线
            sb.append("━━━━━━━━━━━━━━\n");
            
            // Настройки摘要
            sb.append("⚙️ Настройки:\n");
            
            // автоматическиЗапись
            sb.append("• Автозапись: ").append(appConfig.isAutoStartRecording() ? "Вкл" : "Выкл");
            if (appConfig.isAutoStartRecording() && appConfig.isScreenOffRecordingEnabled()) {
                sb.append("+при выкл. экране");
            }
            sb.append("\n");
            
            // Мониторинг
            if (heartbeatManager != null) {
                com.kooo.evcam.heartbeat.HeartbeatConfig hbConfig = heartbeatManager.getConfig();
                if (hbConfig.isEnabled()) {
                    sb.append("• Мониторинг: вкл");
                    if (hbConfig.isScreenOnPushEnabled() && hbConfig.isScreenOffPushEnabled()) {
                        sb.append("(экран вкл+выкл)");
                    } else if (hbConfig.isScreenOnPushEnabled()) {
                        sb.append("(экран вкл)");
                    } else if (hbConfig.isScreenOffPushEnabled()) {
                        sb.append("(экран выкл)");
                    }
                    sb.append("\n");
                } else {
                    sb.append("• Мониторинг: Выкл\n");
                }
            }
            
            // 分时长
            int segmentMin = appConfig.getSegmentDurationMinutes();
            sb.append("• Длительность сегмента: ").append(segmentMin).append(" мин.\n");
            
            // 车型
            sb.append("• Модель: ").append(appConfig.getCarModel());
            
        } catch (Exception e) {
            AppLog.e(TAG, "构建Ошибка получения статуса", e);
            sb.append("ПолучениеОшибка получения статуса: ").append(e.getMessage());
        }
        
        return sb.toString();
    }

    /**
     * 处理Начать записькоманда
     * 唤醒 до Передний план并Начать непрерывную запись（等同点击Запись按钮)
     */
    private String handleStartRecordingCommand() {
        AppLog.d(TAG, "处理Начать записькоманда");
        
        // Если 经 Запись，返回Уведомление
        if (isRecording) {
            return "⚠️ Уже идёт запись, повторный запуск не требуется";
        }
        
        // использование WakeUpHelper 唤醒Приложение并Начать запись
        // 这确保т.е.使 Фоновый режимтакже能正确открытьКамера并Запись
        WakeUpHelper.launchForStartRecording(this);
        
        return "▶️ Начинаю запись...\n\nОтправка「Статус」ПросмотрЗаписьСтатус\nОтправка「Остановить запись」Остановить запись";
    }

    /**
     * 处理Остановить записькоманда
     * Остановить запись并退 до Фоновый режим
     */
    private String handleStopRecordingCommand() {
        AppLog.d(TAG, "处理Остановить записькоманда");
        
        // Если 没有 Запись，返回Уведомление
        if (!isRecording) {
            return "⚠️ Сейчас запись не ведётся";
        }
        
        // 记录Запись时长用于返回Информация
        String durationInfo = "";
        if (recordingStartTime > 0) {
            long elapsedMs = System.currentTimeMillis() - recordingStartTime;
            long totalSeconds = elapsedMs / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            durationInfo = String.format("，Всего Запись %02d:%02d", minutes, seconds);
        }
        
        // использование WakeUpHelper 确保Приложение Передний план后Остановить запись
        // 然后会автоматически退 до Фоновый режим
        WakeUpHelper.launchForStopRecording(this);
        
        return "⏹️ Запись остановлена" + durationInfo + "\nПриложение перейдёт в фоновый режим";
    }

    /**
     * 处理Передний планкоманда
     * 将Приложение переключено на передний план
     */
    private String handleForegroundCommand() {
        AppLog.d(TAG, "处理Передний планкоманда");
        
        // использование WakeUpHelper 将Приложение唤醒 до Передний план
        WakeUpHelper.launchForForeground(this);
        
        return "📱 Приложение переведено на передний план";
    }

    /**
     * 处理Фоновый режимкоманда
     * 将Приложениепереключиться в фоновый режим
     */
    private String handleBackgroundCommand() {
        AppLog.d(TAG, "处理Фоновый режимкоманда");
        
        //  主线程выполнение退 до Фоновый режим
        runOnUiThread(() -> {
            moveTaskToBack(true);
            AppLog.d(TAG, "Приложение переключено в фоновый режим");
        });
        
        return "📴 Приложение переключено в фоновый режим";
    }

    /**
     * 处理Выходкоманда
     */
    private String handleExitCommand(boolean confirmed) {
        AppLog.d(TAG, "处理Выходкоманда，confirmed=" + confirmed);
        
        if (!confirmed) {
            return "⚠️ Подтвердите выход из EVCam?\nОтправьте «Подтвердить выход» для подтверждения。";
        }
        
        //  主线程выполнениеВыход
        runOnUiThread(() -> {
            AppLog.d(TAG, "Выполняется выход...");
            exitApp();
        });
        
        return "👋 EVCam завершает работу...";
    }

    /**
     * ПолучениеDingTalk API 客户端
     */
    public DingTalkApiClient getDingTalkApiClient() {
        return dingTalkApiClient;
    }

    /**
     * ПолучениеDingTalkконфигурация
     */
    public DingTalkConfig getDingTalkConfig() {
        return dingTalkConfig;
    }

    /**
     * ПолучениеТекущийЗаписьСтатус（供Внешнее查询)
     */
    public boolean isCurrentlyRecording() {
        return isRecording;
    }

    /**
     * ОтправкаТекущийЗаписьСтатус广播（供悬浮窗Сервис查询)
     */
    public void broadcastCurrentRecordingState() {
        FloatingWindowService.sendRecordingStateChanged(this, isRecording);
    }
    
    /**
     * перезагрузкаХранилищеОчистка задача（конфигурация更改后调用)
     */
    public void restartStorageCleanupTask() {
        if (storageCleanupManager != null) {
            storageCleanupManager.stop();
        }
        storageCleanupManager = new StorageCleanupManager(this);
        storageCleanupManager.start();
        AppLog.d(TAG, "ХранилищеОчистка задачаперезагрузка");
    }

    /**
     * Уведомление RemoteViewFragment обновление UI
     */
    private void updateRemoteViewFragmentUI() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (fragment instanceof RemoteViewFragment) {
            ((RemoteViewFragment) fragment).updateServiceStatus();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // СохранитьЗаписьСтатус（用于主题切换后Восстановление)
        // 注意：只Сохранить非Удалённая запись Статус，Удалённая запись（DingTalkкоманда)不автоматическиВосстановление
        if (isRecording && !isRemoteRecording) {
            outState.putBoolean("wasRecording", true);
            outState.putLong("recordingStartTime", recordingStartTime);
            outState.putInt("segmentCount", currentSegmentCount);
            AppLog.d(TAG, "onSaveInstanceState: СохранитьЗаписьСтатус - startTime=" + recordingStartTime + ", segment=" + currentSegmentCount);
        } else {
            outState.putBoolean("wasRecording", false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInBackground = true;
        BlindSpotService.notifySelfBackground();
        AppLog.d(TAG, "onPause called, isRecording=" + isRecording);
        
        // ПаузаМониторинг（进入Фоновый режим时)
        if (heartbeatManager != null) {
            heartbeatManager.pause();
        }
        
        // Уведомление悬浮窗Сервис：Приложение进入Фоновый режим，显示悬浮窗
        if (appConfig.isFloatingWindowEnabled()) {
            FloatingWindowService.sendAppForegroundState(this, false);
        }
        
        // 根据 否Выполняется Запись，决定если何处理Камера
        if (cameraManager != null) {
            if (isRecording || isRemoteRecording) {
                // Выполняется Запись（вручнуюилиУдалённый)：保持КамераПодключение（有Передний планСервис保护)
                AppLog.d(TAG, "Recording in progress (manual=" + isRecording + ", remote=" + isRemoteRecording + "), keeping cameras connected");
            } else if (isAutoRecordingPending) {
                // автоматическиЗаписьВыполняется ожидание：保持КамераПодключение（Вкл机自Запуск场景)
                AppLog.d(TAG, "Auto recording pending, keeping cameras connected for startup recording");
            } else if (BlindSpotService.hasActiveCameraWindows()) {
                // 有悬浮窗（补盲/常驻/副屏)Выполняется использованиеКамера：保持Подключение
                // 悬浮窗Закрыто时会自行释放Камера（closeCamerasIfIdle)
                AppLog.d(TAG, "Active camera windows exist, keeping cameras connected");
            } else {
                // Не Запись且无悬浮窗：主动отключеноКамера，释放资源
                AppLog.d(TAG, "Not recording, closing all cameras to release resources");
                cameraManager.closeAllCameras();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        AppLog.d(TAG, "onStop called, isRecording=" + isRecording);
        
        // Если Выполняется Запись但 Activity т.е.将 销毁，提前Остановить запись
        // 这 予比 onDestroy 更充裕 时间来завершениеОчистка 
        if (isRecording && cameraManager != null && isFinishing()) {
            AppLog.d(TAG, "Activity is finishing, stopping recording in onStop for safer cleanup");
            try {
                cameraManager.stopRecording();
                isRecording = false;
                // Остановить запись相Выкл  UI обновление（Activity т.е.将销毁，不显示 Toast)
                stopBlinkAnimation();
                stopRecordingTimer();
                // ОстановкаПередний планСервис
                CameraForegroundService.stop(this);
            } catch (Exception e) {
                AppLog.e(TAG, "Error stopping recording in onStop", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean wasInBackground = isInBackground;
        isInBackground = false;
        BlindSpotService.notifySelfForeground();
        
        // 标记 Activity 经完全Восстановление过一 раз（用于区分新创建 и существует  Activity)
        // 这 шт.标记  onCreate 后Первый раз onResume 时设为 true
        boolean wasFirstResume = !hasBeenResumedOnce;
        hasBeenResumedOnce = true;
        
        AppLog.d(TAG, "onResume called, wasInBackground=" + wasInBackground + ", isRecording=" + isRecording + ", firstResume=" + wasFirstResume);
        
        // Уведомление悬浮窗Сервис：Приложение进入Передний план，隐藏悬浮窗
        if (appConfig.isFloatingWindowEnabled()) {
            FloatingWindowService.sendAppForegroundState(this, true);
        }
        
        // 返回Передний план时，проверкаКамераПодключениеСтатус
        if (cameraManager != null && wasInBackground) {
            // инициализация Handler（Если необходимо)
            if (reopenCameraHandler == null) {
                reopenCameraHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            }
            
            // Отменадо 延迟задача（防抖：避免 onResume  多 раз调用时重复открытьКамера)
            if (reopenCameraRunnable != null) {
                reopenCameraHandler.removeCallbacks(reopenCameraRunnable);
                AppLog.d(TAG, "Cancelled previous camera reopen task (debounce)");
            }
            
            // 创建新 延迟задача
            reopenCameraRunnable = () -> {
                // 只 没有Выполняется Запись时重新открыть（Запись时Камера应该保持Подключение)
                if (!isRecording) {
                    AppLog.d(TAG, "Reopening cameras after returning from background");
                    cameraManager.openAllCameras();
                    
                    // проверка 否有待处理 Удалённыйкоманда
                    if (pendingRemoteCommand) {
                        AppLog.d(TAG, "Has pending remote command, will execute after cameras ready");
                        // ожиданиеКамера准备好后выполнениекоманда（  handleRemoteCommand 处理)
                    }
                    
                    // Если ВключитьавтоматическиЗапись， от Фоновый режим返回时автоматическиВосстановлениеЗапись
                    if (appConfig.isAutoStartRecording()) {
                        AppLog.d(TAG, "ВключитьавтоматическиЗапись， от Фоновый режим返回后将автоматическиВосстановлениеЗапись");
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (!isRecording && cameraManager != null && cameraManager.hasConnectedCameras()) {
                                AppLog.d(TAG, "автоматическиВосстановлениеЗапись...");
                                startRecording();
                                Toast.makeText(this, "Запись автоматически возобновлена", Toast.LENGTH_SHORT).show();
                            }
                        }, 1500);  // ожиданиеКамера准备好
                    }
                } else {
                    AppLog.d(TAG, "Recording in progress, cameras should still be connected");
                }
                
                // ЗапускМониторинг（返回Передний план时，Если Включено)
                if (heartbeatManager != null && heartbeatManager.getConfig().isEnabled()) {
                    // 延迟Запуск，ожиданиеКамера准备好
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (heartbeatManager != null && !isInBackground) {
                            heartbeatManager.start();
                        }
                    }, 1500);
                }
            };
            
            // 延迟100ms后выполнение（只有最后一 раз onResume 会真正выполнение)
            reopenCameraHandler.postDelayed(reopenCameraRunnable, 100);
        }
        // 注意：心跳Сервис自Запуск逻辑移至 initHeartbeatManager() 
        // 因为 onResume выполнение时 HeartbeatManager 可能还没有инициализация
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Activity  重建（主题切换、recreate 等)而非постоянно销毁时，
        // 清掉 Holder  旧 CameraManager，确保新 Activity  от 头инициализацияКамера
        if (!isFinishing()) {
            com.kooo.evcam.camera.CameraManagerHolder.getInstance().setCameraManager(null);
        }

        // Закрыто预览矫正悬浮窗
        dismissPreviewCorrectionFloating();

        // ОстановкаотладкаИнформацияобновление
        stopDebugUpdates();

        // очистка静态实例引用
        if (instance == this) {
            instance = null;
        }
        
        // СохранитьТекущийРабота д.志 до 持久化Файл（用于 разЗапуск时可传"предыдущий сеанс д.志")
        // 放  onDestroy Вкл头，确保 Очистка Другое资源前Сохранить完整 д.志
        AppLog.saveToPersistentLog(this);

        // ОтменаавтоматическиОстановить запись задача
        if (autoStopHandler != null && autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }
        
        // ОстановкаавтоматическиЗаписьПлановая проверка
        stopAutoRecordingCheck();
        
        // СбросУдалённая записьСтатус
        isRemoteRecording = false;
        wasManualRecordingBeforeRemote = false;
        
        // Очистка Удалённыйкоманда分发器
        if (remoteCommandDispatcher != null) {
            remoteCommandDispatcher.cleanup();
        }
        
        // Очистка Мониторингуправление器
        if (heartbeatManager != null) {
            heartbeatManager.destroy();
            heartbeatManager = null;
        }
        
        // Очистка 息屏Запись相Выкл资源
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception e) {
                AppLog.w(TAG, "注销息屏广播接收器时出错: " + e.getMessage());
            }
            screenStateReceiver = null;
        }
        
        // Очистка Фоновый режим切换广播接收器
        if (backgroundCommandReceiver != null) {
            try {
                unregisterReceiver(backgroundCommandReceiver);
            } catch (Exception e) {
                AppLog.w(TAG, "注销Фоновый режим切换广播接收器时出错: " + e.getMessage());
            }
            backgroundCommandReceiver = null;
        }
        if (screenStateHandler != null) {
            if (screenOffStopRunnable != null) {
                screenStateHandler.removeCallbacks(screenOffStopRunnable);
            }
            if (screenOnStartRunnable != null) {
                screenStateHandler.removeCallbacks(screenOnStartRunnable);
            }
            if (screenOffBackgroundRunnable != null) {
                screenStateHandler.removeCallbacks(screenOffBackgroundRunnable);
            }
        }

        // ОстановкаПередний планСервис（确保Очистка )
        CameraForegroundService.stop(this);

        // 【重要】不再  onDestroy ОстановкаУдалённыйСервис
        // 原因：某些车机Система（если星舰7)会 Фоновый режим强杀 Activity，但进程仍存活
        // УдалённыйСервис通过 RemoteServiceManager 以单例режимРабота，可以продолжить接收команда
        // 只有用户明确调用 exitApp() 时才Остановка所有УдалённыйСервис
        AppLog.d(TAG, "onDestroy: УдалённыйСервис由 RemoteServiceManager управление，不 此Остановка");
        
        // ОстановкаХранилищеОчистка задача
        if (storageCleanupManager != null) {
            storageCleanupManager.stop();
        }
        
        // ОстановкаФайл传输Сервис
        FileTransferManager.getInstance(this).stop();

        // 带таймаут保护 Камера资源释放
        if (cameraManager != null) {
            releaseCameraManagerWithTimeout(3000);  // 3 сек.таймаут
        }
        
        // СбросавтоматическиЗапись触发标志（ разЗапуск时可以再 раз触发)
        autoStartRecordingTriggered = false;
    }
    
    /**
     * 带таймаут保护 Камерауправление器释放
     * 防止 release() операция阻塞过久导致 ANR
     * 
     * @param timeoutMs таймаут时间（毫 сек.)
     */
    private void releaseCameraManagerWithTimeout(long timeoutMs) {
        if (cameraManager == null) {
            return;
        }
        
        final CountDownLatch latch = new CountDownLatch(1);
        
        //  Фоновый режим线程выполнение release，避免阻塞主线程
        new Thread(() -> {
            try {
                AppLog.d(TAG, "Releasing camera manager in background thread...");
                cameraManager.release();
                AppLog.d(TAG, "Camera manager released successfully");
            } catch (Exception e) {
                AppLog.e(TAG, "Error releasing camera manager", e);
            } finally {
                latch.countDown();
            }
        }, "CameraRelease").start();
        
        try {
            // ожидание release завершение，但Настройкитаймаут避免 ANR
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                AppLog.w(TAG, "Camera manager release timed out after " + timeoutMs + "ms, " +
                        "resources may not be fully released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppLog.w(TAG, "Camera manager release interrupted");
        }
    }

    /**
     * 显示Записьаномалия Уведомление（автоматически消失，每20 сек.最多显示一 раз)
     */
    private void showCorruptedFilesDeletedDialog(List<String> deletedFiles) {
        if (deletedFiles == null || deletedFiles.isEmpty()) {
            return;
        }

        // 记录 д.志（始终记录)
        AppLog.w(TAG, "Recording error, deleted " + deletedFiles.size() + " corrupted files: " + deletedFiles);

        // проверка 否可以显示 Toast（20 сек.内只显示一 раз)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRecordingErrorToastTime < RECORDING_ERROR_TOAST_INTERVAL) {
            AppLog.d(TAG, "Recording error toast suppressed (rate limited)");
            return;
        }
        lastRecordingErrorToastTime = currentTime;

        runOnUiThread(() -> {
            android.widget.Toast.makeText(this, "Recording error occurred", android.widget.Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // Если  Fragment 返回栈不пусто（ 二级菜单)，则返回一级
            getSupportFragmentManager().popBackStack();
            AppLog.d(TAG, "Popped fragment back stack, returning to previous screen");
        } else if (fragmentContainer != null && fragmentContainer.getVisibility() == View.VISIBLE) {
            // Если Текущий 非Запись界面（Fragment界面)，先返回Запись界面
            goToRecordingInterface();
            AppLog.d(TAG, "Returned to recording interface via back button");
        } else {
            //  Запись界面，按返回键将Приложение移 до Фоновый режим，而不 ЗакрытоActivity
            // 这样 разоткрытьПриложение时能快速Восстановление，无需重新创建Activity
            moveTaskToBack(true);
            AppLog.d(TAG, "Moved to background via back button");
        }
    }
    
    // ==================== 亮度/Шумоподавление调节相Выкл方法 ====================
    
    /**
     * Получение亮度/Шумоподавление调节управление器
     * @return ImageAdjustManager 实例
     */
    public ImageAdjustManager getImageAdjustManager() {
        return imageAdjustManager;
    }
    
    /**
     * 注册Камера до 亮度/Шумоподавление调节управление器
     */
    private void registerCamerasToImageAdjustManager() {
        if (imageAdjustManager == null || cameraManager == null) {
            return;
        }
        
        // 清空до注册 Камера
        imageAdjustManager.clearCameras();
        
        // 注册各Позиция Камера
        String[] positions = {"front", "back", "left", "right"};
        for (String position : positions) {
            SingleCamera camera = cameraManager.getCamera(position);
            if (camera != null) {
                imageAdjustManager.registerCamera(camera);
            }
        }
        
        // Если Включить亮度/Шумоподавление调节，Настройки各Камера ВключитьСтатус
        boolean enabled = appConfig.isImageAdjustEnabled();
        if (enabled) {
            setImageAdjustEnabled(true);
        }
        
        AppLog.d(TAG, "Registered cameras to ImageAdjustManager, adjust enabled: " + enabled);
    }
    
    // ==================== Мониторинг相Выкл方法 ====================
    
    /**
     * инициализацияМониторингуправление器
     */
    private void initHeartbeatManager() {
        if (heartbeatManager == null) {
            heartbeatManager = new com.kooo.evcam.heartbeat.HeartbeatManager(this);
        }
        
        // Настройки相机列表（去重，避免同一 шт.物理相机 添加多 раз)
        if (cameraManager != null) {
            List<SingleCamera> cameras = new ArrayList<>();
            java.util.Set<String> addedCameraIds = new java.util.HashSet<>();
            
            String[] positions = {"front", "back", "left", "right"};
            for (String position : positions) {
                SingleCamera camera = cameraManager.getCamera(position);
                if (camera != null) {
                    String cameraId = camera.getCameraId();
                    // 只添加Не 添加过 相机（基于物理相机ID去重)
                    if (!addedCameraIds.contains(cameraId)) {
                        cameras.add(camera);
                        addedCameraIds.add(cameraId);
                        AppLog.d(TAG, "HeartbeatManager 添加相机: position=" + position + ", cameraId=" + cameraId);
                    }
                }
            }
            heartbeatManager.setCameras(cameras);
            AppLog.d(TAG, "HeartbeatManager 相机数量: " + cameras.size());
        }
        
        // НастройкиСтатус提供者
        heartbeatManager.setStatusProvider(() -> buildHeartbeatStatusJson());
        
        // Настройки Activity 控制器（用于息屏推图)
        heartbeatManager.setActivityController(new com.kooo.evcam.heartbeat.HeartbeatManager.ActivityController() {
            @Override
            public boolean isInBackground() {
                return isInBackground;
            }
            
            @Override
            public boolean isRecording() {
                return isRecording;
            }
            
            @Override
            public boolean shouldKeepForeground() {
                // Если Вкл启автоматическиЗапись+息屏Запись，необходимо保持Передний план
                return appConfig.isAutoStartRecording() && appConfig.isScreenOffRecordingEnabled();
            }
            
            @Override
            public void wakeUpToForeground() {
                Intent intent = new Intent(MainActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
            
            @Override
            public void moveToBackground() {
                moveTaskToBack(true);
            }
            
            @Override
            public void openCameras() {
                if (cameraManager != null) {
                    cameraManager.openAllCameras();
                }
            }
            
            @Override
            public void closeCameras() {
                if (cameraManager != null) {
                    cameraManager.closeAllCameras();
                }
            }
            
            @Override
            public boolean hasCamerasConnected() {
                return cameraManager != null && cameraManager.hasConnectedCameras();
            }
        });
        
        AppLog.d(TAG, "HeartbeatManager initialized");
        
        // проверка 否необходимо自Запуск心跳Сервис
        // 必须  HeartbeatManager инициализациязавершение后выполнение，不能放  onResume 
        // 因为 onResume выполнение时 HeartbeatManager 可能还没有инициализация
        com.kooo.evcam.heartbeat.HeartbeatConfig hbConfig = heartbeatManager.getConfig();
        if (hbConfig.isAutoStartEnabled() && hbConfig.isConfigured()) {
            AppLog.d(TAG, "心跳СервисавтоматическиЗапускпроверка：autoStart=true, configured=true");
            // 延迟Запуск，ожидание相机完全绪
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (heartbeatManager != null) {
                    heartbeatManager.onConfigChanged();
                }
            }, 1500);
        }
    }
    
    /**
     * Получение心跳управление器（供 Fragment 调用)
     */
    public com.kooo.evcam.heartbeat.HeartbeatManager getHeartbeatManager() {
        return heartbeatManager;
    }
    
    /**
     * ПолучениеПодключено Камера数量
     */
    public int getConnectedCameraCount() {
        if (cameraManager != null) {
            return cameraManager.getConnectedCameraCount();
        }
        return 0;
    }
    
    /**
     * Получениеконфигурация Камера总数
     */
    public int getTotalCameraCount() {
        return configuredCameraCount;
    }
    
    /**
     * 心跳конфигурация变更时调用（ от  HeartbeatFragment 调用)
     */
    public void onHeartbeatConfigChanged() {
        if (heartbeatManager != null) {
            heartbeatManager.onConfigChanged();
        }
    }
    
    /**
     * 构建Мониторинг Статус JSON
     */
    private String buildHeartbeatStatusJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        // ЗаписьСтатус
        sb.append("\"isRecording\":").append(isRecording).append(",");
        if (isRecording && recordingStartTime > 0) {
            long elapsed = System.currentTimeMillis() - recordingStartTime;
            sb.append("\"recordingDurationMs\":").append(elapsed).append(",");
        }
        
        // ХранилищеИнформация
        try {
            File storageDir = StorageHelper.getVideoDir(this);
            long availableSpace = StorageHelper.getAvailableSpace(storageDir);
            sb.append("\"storageLocation\":\"").append(escapeJsonString(appConfig.getStorageLocation())).append("\",");
            sb.append("\"availableSpaceBytes\":").append(availableSpace).append(",");
            sb.append("\"availableSpaceText\":\"").append(escapeJsonString(StorageHelper.formatSize(availableSpace))).append("\",");
        } catch (Exception e) {
            sb.append("\"storageLocation\":\"unknown\",");
            sb.append("\"availableSpaceBytes\":0,");
            sb.append("\"availableSpaceText\":\"Неизвестно\",");
        }
        
        // конфигурацияИнформация
        sb.append("\"carModel\":\"").append(escapeJsonString(appConfig.getCarModel())).append("\",");
        sb.append("\"segmentDurationMinutes\":").append(appConfig.getSegmentDurationMinutes()).append(",");
        sb.append("\"resolution\":\"").append(escapeJsonString(appConfig.getTargetResolution())).append("\",");
        
        // 相机Статус
        int connectedCameras = cameraManager != null ? cameraManager.getConnectedCameraCount() : 0;
        sb.append("\"connectedCameras\":").append(connectedCameras).append(",");
        sb.append("\"totalCameras\":").append(configuredCameraCount).append(",");
        
        // App Информация
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            sb.append("\"appVersion\":\"").append(escapeJsonString(versionName)).append("\",");
        } catch (Exception e) {
            sb.append("\"appVersion\":\"unknown\",");
        }
        
        // 时间戳
        sb.append("\"timestamp\":").append(System.currentTimeMillis());
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 转义 JSON 字符串
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Настройки亮度/Шумоподавление调节ВключитьСтатус
     * @param enabled true 表示Включить
     */
    public void setImageAdjustEnabled(boolean enabled) {
        if (cameraManager == null) {
            return;
        }
        
        // Настройки各Камера ВключитьСтатус
        String[] positions = {"front", "back", "left", "right"};
        for (String position : positions) {
            SingleCamera camera = cameraManager.getCamera(position);
            if (camera != null) {
                camera.setImageAdjustEnabled(enabled);
            }
        }
        
        // Если Включить，立т.е.ПриложениеТекущие настройки 参数
        if (enabled && imageAdjustManager != null) {
            // 延迟выполнение，确保Камера会话经конфигурация好
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                imageAdjustManager.updateAllCameras();
            }, 500);
        }
        
        AppLog.d(TAG, "Image adjust enabled: " + enabled);
    }
    
    /**
     * 显示亮度/Шумоподавление调节悬浮窗
     * 悬浮窗由 MainActivity управление，这样т.е.使ВыходНастройки页面также能保持显示
     */
    public void showImageAdjustFloatingWindow() {
        // проверкаРазрешение плавающего окна
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Floating window permission required to open adjustment window", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }
        
        if (imageAdjustManager == null) {
            Toast.makeText(this, "Camera not ready, cannot open adjustment window", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Закрытодо 悬浮窗（Если 有)
        if (imageAdjustFloatingWindow != null && imageAdjustFloatingWindow.isShowing()) {
            imageAdjustFloatingWindow.dismiss();
        }
        
        // 创建并显示悬浮窗
        imageAdjustFloatingWindow = new ImageAdjustFloatingWindow(this, imageAdjustManager);
        imageAdjustFloatingWindow.setOnDismissListener(() -> {
            AppLog.d(TAG, "Image adjust floating window dismissed");
        });
        imageAdjustFloatingWindow.show();
        
        AppLog.d(TAG, "Image adjust floating window shown");
    }
    
    /**
     * Закрыто亮度/Шумоподавление调节悬浮窗
     */
    public void dismissImageAdjustFloatingWindow() {
        if (imageAdjustFloatingWindow != null && imageAdjustFloatingWindow.isShowing()) {
            imageAdjustFloatingWindow.dismiss();
            imageAdjustFloatingWindow = null;
        }
    }
    
    /**
     * проверка亮度/Шумоподавление调节悬浮窗 否Выполняется 显示
     */
    public boolean isImageAdjustFloatingWindowShowing() {
        return imageAdjustFloatingWindow != null && imageAdjustFloatingWindow.isShowing();
    }
    
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
                // Разрешениепредоставить，открыть悬浮窗
                showImageAdjustFloatingWindow();
            } else {
                Toast.makeText(this, "Floating window permission not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // ==================== 静态实例доступ ====================
    
    /**
     * Получение MainActivity 实例
     * 用于 CameraForegroundService проверка Activity  否 Работа
     * 
     * @return MainActivity 实例，Если  Activity Не 创建или销毁则Возвращает null
     */
    public static MainActivity getInstance() {
        return instance;
    }
    
    /**
     * 显示Камера预览悬浮窗
     * 
     * @param cameraPosition 要显示 КамераПозиция（front/back/left/right)
     */
    public void showCameraPreviewFloating(String cameraPosition) {
        // проверкаРазрешение плавающего окна
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Floating window permission required to show preview", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        
        // TODO: CameraPreviewFloatingService 尚Не 实现
        // CameraPreviewFloatingService.start(this, cameraPosition);
        AppLog.d(TAG, "Camera preview floating not implemented yet for: " + cameraPosition);
        Toast.makeText(this, "Camera preview floating window feature not implemented yet", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * ЗакрытоКамера预览悬浮窗
     */
    public void dismissCameraPreviewFloating() {
        // TODO: CameraPreviewFloatingService 尚Не 实现
        // CameraPreviewFloatingService.stop(this);
        AppLog.d(TAG, "Camera preview floating stop - not implemented yet");
    }
}
