package com.kooo.evcam;

import android.content.Context;

import java.lang.ref.WeakReference;

import com.kooo.evcam.dingtalk.DingTalkApiClient;
import com.kooo.evcam.dingtalk.DingTalkConfig;
import com.kooo.evcam.dingtalk.DingTalkStreamManager;
import com.kooo.evcam.telegram.TelegramApiClient;
import com.kooo.evcam.telegram.TelegramBotManager;
import com.kooo.evcam.telegram.TelegramConfig;
import com.kooo.evcam.feishu.FeishuApiClient;
import com.kooo.evcam.feishu.FeishuBotManager;
import com.kooo.evcam.feishu.FeishuConfig;

/**
 * УдалённыйСервисуправление器（单例)
 * управлениеDingTalk и  Telegram Сервис 生命周期，确保  Activity 重建时Сервис不会断
 * 这 шт.类持有Сервис实例 强引用，避免 垃圾回收
 *
 * 【重要】Сервис持久化策略：
 * 1. 单例режим确保Сервис实例 Приложение进程存活期间始终Доступно
 * 2. т.е.使 MainActivity  Система杀死，只要进程还 ，СервиспродолжитьРабота
 * 3. 配合 CameraForegroundService（Передний планСервис)提升进程优先级，降Низкий 杀概率
 * 4. Сервис只 и ниже情况Остановка：
 *    - 用户明确调用 stopDingTalkService() / stopTelegramService()
 *    - 用户Выход из приложения（exitApp())
 *    - Приложение进程 Система完全杀死（此时所有资源все 回收)
 *
 * 【车机Система适配】
 * - 不依赖 Activity.isFinishing() 判断Сервис 否Остановка
 * - 某些深度定制  Android Система（если车机Система) Фоновый режим强杀 Activity 时
 *   isFinishing() 可能Ошибка返回 true，导致误判为用户主动Выход
 * - 新策略：Сервис生命周期 и  Activity 生命周期完全解耦
 */
public class RemoteServiceManager {
    private static final String TAG = "RemoteServiceManager";
    private static RemoteServiceManager instance;

    // DingTalkСервис（强引用，避免  GC)
    private DingTalkStreamManager dingTalkStreamManager;
    private DingTalkApiClient dingTalkApiClient;

    // Telegram Сервис（强引用，避免  GC)
    private TelegramBotManager telegramBotManager;
    private TelegramApiClient telegramApiClient;

    // FeishuСервис（强引用，避免  GC)
    private FeishuBotManager feishuBotManager;
    private FeishuApiClient feishuApiClient;
    
    // Запуск锁，防止竞态条件
    private volatile boolean isDingTalkStarting = false;
    private volatile boolean isTelegramStarting = false;
    private volatile boolean isFeishuStarting = false;
    private final Object dingTalkLock = new Object();
    private final Object telegramLock = new Object();
    private final Object feishuLock = new Object();
    
    // СтатусИнформация提供者（当 MainActivity Запуск后会注册，использование弱引用避免内存泄漏)
    private WeakReference<StatusInfoProvider> statusInfoProviderRef;

    /**
     * СтатусИнформация提供者接口
     * 由 MainActivity 实现，提供完整 СтатусИнформация
     */
    public interface StatusInfoProvider {
        String getFullStatusInfo();
    }
    
    private RemoteServiceManager() {
        // 私有构造函数，确保单例
        AppLog.d(TAG, "RemoteServiceManager instance created");
    }
    
    /**
     * 注册СтатусИнформация提供者（MainActivity Запуск时调用)
     * использование弱引用避免 Activity 内存泄漏
     */
    public void setStatusInfoProvider(StatusInfoProvider provider) {
        this.statusInfoProviderRef = new WeakReference<>(provider);
        AppLog.d(TAG, "StatusInfoProvider registered (WeakReference)");
    }
    
    /**
     * очисткаСтатусИнформация提供者（MainActivity 销毁时调用)
     */
    public void clearStatusInfoProvider() {
        this.statusInfoProviderRef = null;
        AppLog.d(TAG, "StatusInfoProvider cleared");
    }
    
    /**
     * ПолучениеСтатусИнформация
     * Если 有 MainActivity 提供者且действует，использование完整Информация；否则использование基本Информация
     */
    public String getStatusInfo(Context context) {
        if (statusInfoProviderRef != null) {
            StatusInfoProvider provider = statusInfoProviderRef.get();
            if (provider != null) {
                try {
                    String fullInfo = provider.getFullStatusInfo();
                    if (fullInfo != null) {
                        return fullInfo;
                    }
                    // Возвращает null 表示 Activity 销毁，использование基本Информация
                    AppLog.d(TAG, "StatusInfoProvider Возвращает null，Activity 可能销毁");
                } catch (Exception e) {
                    AppLog.e(TAG, "Получение完整Ошибка получения статуса，использование基本Информация", e);
                }
            } else {
                // 弱引用 回收，Очистка 引用
                statusInfoProviderRef = null;
                AppLog.d(TAG, "StatusInfoProvider  回收，использование基本Информация");
            }
        }
        return buildBasicStatusInfo(context);
    }

    public static synchronized RemoteServiceManager getInstance() {
        if (instance == null) {
            instance = new RemoteServiceManager();
        }
        return instance;
    }

    // ==================== DingTalk Сервисуправление ====================

    public void setDingTalkService(DingTalkStreamManager manager, DingTalkApiClient apiClient) {
        this.dingTalkStreamManager = manager;
        this.dingTalkApiClient = apiClient;
        AppLog.d(TAG, "DingTalk service registered");
    }

    public DingTalkStreamManager getDingTalkStreamManager() {
        return dingTalkStreamManager;
    }

    public DingTalkApiClient getDingTalkApiClient() {
        return dingTalkApiClient;
    }

    public boolean isDingTalkRunning() {
        return dingTalkStreamManager != null && dingTalkStreamManager.isRunning();
    }
    
    /**
     * проверкаDingTalkСервис 否Выполняется Запускили Работа
     * 用于防止竞态条件创建重复实例
     */
    public boolean isDingTalkStartingOrRunning() {
        synchronized (dingTalkLock) {
            return isDingTalkRunning() || isDingTalkStarting;
        }
    }

    public void clearDingTalkService() {
        if (dingTalkStreamManager != null) {
            dingTalkStreamManager.stop();
        }
        this.dingTalkStreamManager = null;
        this.dingTalkApiClient = null;
        AppLog.d(TAG, "DingTalk service cleared");
    }

    // ==================== Telegram Сервисуправление ====================

    public void setTelegramService(TelegramBotManager manager, TelegramApiClient apiClient) {
        this.telegramBotManager = manager;
        this.telegramApiClient = apiClient;
        AppLog.d(TAG, "Telegram service registered");
    }

    public TelegramBotManager getTelegramBotManager() {
        return telegramBotManager;
    }

    public TelegramApiClient getTelegramApiClient() {
        return telegramApiClient;
    }

    public boolean isTelegramRunning() {
        return telegramBotManager != null && telegramBotManager.isRunning();
    }
    
    /**
     * проверка Telegram Сервис 否Выполняется Запускили Работа
     * 用于防止竞态条件创建重复实例
     */
    public boolean isTelegramStartingOrRunning() {
        synchronized (telegramLock) {
            return isTelegramRunning() || isTelegramStarting;
        }
    }

    public void clearTelegramService() {
        if (telegramBotManager != null) {
            telegramBotManager.stop();
        }
        this.telegramBotManager = null;
        this.telegramApiClient = null;
        AppLog.d(TAG, "Telegram service cleared");
    }

    // ==================== FeishuСервисуправление ====================

    public void setFeishuService(FeishuBotManager manager, FeishuApiClient apiClient) {
        this.feishuBotManager = manager;
        this.feishuApiClient = apiClient;
        AppLog.d(TAG, "Feishu service registered");
    }

    public FeishuBotManager getFeishuBotManager() {
        return feishuBotManager;
    }

    public FeishuApiClient getFeishuApiClient() {
        return feishuApiClient;
    }

    public boolean isFeishuRunning() {
        return feishuBotManager != null && feishuBotManager.isRunning();
    }

    /**
     * проверкаFeishuСервис 否Выполняется Запускили Работа
     */
    public boolean isFeishuStartingOrRunning() {
        synchronized (feishuLock) {
            return isFeishuRunning() || isFeishuStarting;
        }
    }

    public void clearFeishuService() {
        if (feishuBotManager != null) {
            feishuBotManager.stop();
        }
        this.feishuBotManager = null;
        this.feishuApiClient = null;
        AppLog.d(TAG, "Feishu service cleared");
    }

    // ==================== 通用方法 ====================

    /**
     * проверка 否有任何УдалённыйСервис Работа
     */
    public boolean hasAnyServiceRunning() {
        return isDingTalkRunning() || isTelegramRunning() || isFeishuRunning();
    }

    /**
     * Остановка所有Сервис
     */
    public void stopAllServices() {
        AppLog.d(TAG, "Stopping all remote services");
        clearDingTalkService();
        clearTelegramService();
        clearFeishuService();
    }

    /**
     * ПолучениеСервисСтатус描述（用于Передний планСервисУведомление)
     */
    public String getServiceStatusDescription() {
        StringBuilder sb = new StringBuilder();
        if (isDingTalkRunning()) {
            sb.append("Удалённый сервис DingTalk работает");
        }
        if (isTelegramRunning()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append("Удалённый сервис Telegram работает");
        }
        if (isFeishuRunning()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append("Удалённый сервис Feishu работает");
        }
        if (sb.length() == 0) {
            sb.append("Удалённый сервис работает");
        }
        return sb.toString();
    }

    // ====================  от  Service ЗапускУдалённыйСервис ====================

    /**
     *  от  CameraForegroundService Запускконфигурация好 УдалённыйСервис
     * 这样УдалённыйСервис不依赖 MainActivity  生命周期
     * Получена команда: команда后通过 WakeUpHelper 唤醒 MainActivity выполнение
     */
    public void startRemoteServicesFromService(Context context) {
        AppLog.d(TAG, " от  Service ЗапускУдалённыйСервис...");
        
        // использование ApplicationContext 避免 Service 生命周期问题
        Context appContext = context.getApplicationContext();

        // ЗапускDingTalkСервис
        DingTalkConfig dingTalkConfig = new DingTalkConfig(appContext);
        if (dingTalkConfig.isConfigured() && dingTalkConfig.isAutoStart() && !isDingTalkRunning()) {
            startDingTalkFromService(appContext, dingTalkConfig);
        }

        // Запуск Telegram Сервис
        TelegramConfig telegramConfig = new TelegramConfig(appContext);
        if (telegramConfig.isConfigured() && telegramConfig.isAutoStart() && !isTelegramRunning()) {
            startTelegramFromService(appContext, telegramConfig);
        }

        // ЗапускFeishuСервис
        FeishuConfig feishuConfig = new FeishuConfig(appContext);
        if (feishuConfig.isConfigured() && feishuConfig.isAutoStart() && !isFeishuRunning()) {
            startFeishuFromService(appContext, feishuConfig);
        }
    }

    /**
     *  от  Service ЗапускDingTalkСервис
     */
    private void startDingTalkFromService(Context context, DingTalkConfig config) {
        // 防止竞态条件：加锁проверка
        synchronized (dingTalkLock) {
            if (isDingTalkRunning() || isDingTalkStarting) {
                AppLog.d(TAG, "DingTalkСервис РаботаилиВыполняется Запуск，跳过");
                return;
            }
            isDingTalkStarting = true;
        }
        
        AppLog.d(TAG, " от  Service ЗапускDingTalkСервис...");

        try {
            DingTalkApiClient apiClient = new DingTalkApiClient(config);

            DingTalkStreamManager.ConnectionCallback connectionCallback = new DingTalkStreamManager.ConnectionCallback() {
                @Override
                public void onConnected() {
                    AppLog.d(TAG, "DingTalkСервисПодключено（ от  Service Запуск)");
                }

                @Override
                public void onDisconnected() {
                    AppLog.d(TAG, "DingTalkСервисотключено（ от  Service Запуск)");
                }

                @Override
                public void onError(String error) {
                    AppLog.e(TAG, "DingTalkСервисОшибка（ от  Service Запуск): " + error);
                }
            };

            // 简化 команда回调 - Получена команда: команда后通过 WakeUpHelper 唤醒 MainActivity выполнение
            DingTalkStreamManager.CommandCallback commandCallback = new DingTalkStreamManager.CommandCallback() {
                @Override
                public void onRecordCommand(String conversationId, String conversationType, String userId, int durationSeconds) {
                    // 通过 WakeUpHelper 唤醒 MainActivity выполнение
                    WakeUpHelper.launchForRecording(context, conversationId, conversationType, userId, durationSeconds);
                }

                @Override
                public void onPhotoCommand(String conversationId, String conversationType, String userId) {
                    WakeUpHelper.launchForPhoto(context, conversationId, conversationType, userId);
                }

                @Override
                public String getStatusInfo() {
                    // 优先использование MainActivity 提供 完整СтатусИнформация
                    return RemoteServiceManager.this.getStatusInfo(context);
                }

                @Override
                public String onStartRecordingCommand() {
                    WakeUpHelper.launchForStartRecording(context);
                    return "✅ Начинаю запись...";
                }

                @Override
                public String onStopRecordingCommand() {
                    WakeUpHelper.launchForStopRecording(context);
                    return "✅ Выполняется Остановить запись...";
                }

                @Override
                public String onExitCommand(boolean confirmed) {
                    if (confirmed) {
                        // Остановка所有Сервис
                        stopAllServices();
                        return "✅ EVCam Выход";
                    }
                    return "⚠️ Отправьте «Подтвердить выход» для подтверждения";
                }

                @Override
                public String onForegroundCommand() {
                    WakeUpHelper.launchForForeground(context);
                    return "📱 Приложение переключено на передний план";
                }

                @Override
                public String onBackgroundCommand() {
                    // использование广播Уведомление Activity 退Фоновый режим，避免Запуск Activity 导致闪屏
                    WakeUpHelper.sendBackgroundBroadcast(context);
                    return "📴 Приложение переключено в фоновый режим";
                }
            };

            DingTalkStreamManager streamManager = new DingTalkStreamManager(context, config, apiClient, connectionCallback);
            streamManager.start(commandCallback, true);

            // 注册 до управление器
            setDingTalkService(streamManager, apiClient);
            AppLog.d(TAG, "DingTalkСервисЗапускУспешно（ от  Service)");

        } catch (Exception e) {
            AppLog.e(TAG, " от  Service ЗапускDingTalkСервисОшибка", e);
        } finally {
            synchronized (dingTalkLock) {
                isDingTalkStarting = false;
            }
        }
    }

    /**
     *  от  Service Запуск Telegram Сервис
     */
    private void startTelegramFromService(Context context, TelegramConfig config) {
        // 防止竞态条件：加锁проверка
        synchronized (telegramLock) {
            if (isTelegramRunning() || isTelegramStarting) {
                AppLog.d(TAG, "Telegram Сервис РаботаилиВыполняется Запуск，跳过");
                return;
            }
            isTelegramStarting = true;
        }
        
        AppLog.d(TAG, " от  Service Запуск Telegram Сервис...");

        try {
            TelegramApiClient apiClient = new TelegramApiClient(config);

            TelegramBotManager.ConnectionCallback connectionCallback = new TelegramBotManager.ConnectionCallback() {
                @Override
                public void onConnected() {
                    AppLog.d(TAG, "Telegram СервисПодключено（ от  Service Запуск)");
                }

                @Override
                public void onDisconnected() {
                    AppLog.d(TAG, "Telegram Сервисотключено（ от  Service Запуск)");
                }

                @Override
                public void onError(String error) {
                    AppLog.e(TAG, "Telegram СервисОшибка（ от  Service Запуск): " + error);
                }
            };

            // 简化 команда回调
            TelegramBotManager.CommandCallback commandCallback = new TelegramBotManager.CommandCallback() {
                @Override
                public void onRecordCommand(long chatId, int durationSeconds) {
                    WakeUpHelper.launchForRecordingTelegram(context, chatId, durationSeconds);
                }

                @Override
                public void onPhotoCommand(long chatId) {
                    WakeUpHelper.launchForPhotoTelegram(context, chatId);
                }

                @Override
                public String getStatusInfo() {
                    // 优先использование MainActivity 提供 完整СтатусИнформация
                    return RemoteServiceManager.this.getStatusInfo(context);
                }

                @Override
                public String onStartRecordingCommand() {
                    WakeUpHelper.launchForStartRecording(context);
                    return "✅ Начинаю запись...";
                }

                @Override
                public String onStopRecordingCommand() {
                    WakeUpHelper.launchForStopRecording(context);
                    return "✅ Выполняется Остановить запись...";
                }

                @Override
                public String onExitCommand(boolean confirmed) {
                    if (confirmed) {
                        stopAllServices();
                        return "✅ EVCam Выход";
                    }
                    return "⚠️ Отправьте «Подтвердить выход» для подтверждения";
                }

                @Override
                public String onForegroundCommand() {
                    WakeUpHelper.launchForForeground(context);
                    return "📱 Приложение переключено на передний план";
                }

                @Override
                public String onBackgroundCommand() {
                    // использование广播Уведомление Activity 退Фоновый режим，避免Запуск Activity 导致闪屏
                    WakeUpHelper.sendBackgroundBroadcast(context);
                    return "📴 Приложение переключено в фоновый режим";
                }
            };

            TelegramBotManager botManager = new TelegramBotManager(context, config, apiClient, connectionCallback);
            botManager.start(commandCallback);

            // 注册 до управление器
            setTelegramService(botManager, apiClient);
            AppLog.d(TAG, "Telegram СервисЗапускУспешно（ от  Service)");

        } catch (Exception e) {
            AppLog.e(TAG, " от  Service Запуск Telegram СервисОшибка", e);
        } finally {
            synchronized (telegramLock) {
                isTelegramStarting = false;
            }
        }
    }

    /**
     *  от  Service ЗапускFeishuСервис
     */
    private void startFeishuFromService(Context context, FeishuConfig config) {
        // 防止竞态条件：加锁проверка
        synchronized (feishuLock) {
            if (isFeishuRunning() || isFeishuStarting) {
                AppLog.d(TAG, "FeishuСервис РаботаилиВыполняется Запуск，跳过");
                return;
            }
            isFeishuStarting = true;
        }

        AppLog.d(TAG, " от  Service ЗапускFeishuСервис...");

        try {
            FeishuApiClient apiClient = new FeishuApiClient(config);

            FeishuBotManager.ConnectionCallback connectionCallback = new FeishuBotManager.ConnectionCallback() {
                @Override
                public void onConnected() {
                    AppLog.d(TAG, "FeishuСервисПодключено（ от  Service Запуск)");
                }

                @Override
                public void onDisconnected() {
                    AppLog.d(TAG, "FeishuСервисотключено（ от  Service Запуск)");
                }

                @Override
                public void onError(String error) {
                    AppLog.e(TAG, "FeishuСервисОшибка（ от  Service Запуск): " + error);
                }
            };

            // 简化 команда回调
            FeishuBotManager.CommandCallback commandCallback = new FeishuBotManager.CommandCallback() {
                @Override
                public void onRecordCommand(String chatId, String messageId, int durationSeconds) {
                    WakeUpHelper.launchForRecordingFeishu(context, chatId, messageId, durationSeconds);
                }

                @Override
                public void onPhotoCommand(String chatId, String messageId) {
                    WakeUpHelper.launchForPhotoFeishu(context, chatId, messageId);
                }

                @Override
                public String getStatusInfo() {
                    return RemoteServiceManager.this.getStatusInfo(context);
                }

                @Override
                public String onStartRecordingCommand() {
                    WakeUpHelper.launchForStartRecording(context);
                    return "✅ Начинаю запись...";
                }

                @Override
                public String onStopRecordingCommand() {
                    WakeUpHelper.launchForStopRecording(context);
                    return "✅ Выполняется Остановить запись...";
                }

                @Override
                public String onExitCommand(boolean confirmed) {
                    if (confirmed) {
                        stopAllServices();
                        return "✅ EVCam Выход";
                    }
                    return "⚠️ Отправьте «Подтвердить выход» для подтверждения";
                }

                @Override
                public String onForegroundCommand() {
                    WakeUpHelper.launchForForeground(context);
                    return "📱 Приложение переключено на передний план";
                }

                @Override
                public String onBackgroundCommand() {
                    // использование广播Уведомление Activity 退Фоновый режим，避免Запуск Activity 导致闪屏
                    WakeUpHelper.sendBackgroundBroadcast(context);
                    return "📴 Приложение переключено в фоновый режим";
                }
            };

            FeishuBotManager botManager = new FeishuBotManager(context, config, apiClient, connectionCallback);
            botManager.start(commandCallback);

            // 注册 до управление器
            setFeishuService(botManager, apiClient);
            AppLog.d(TAG, "FeishuСервисЗапускУспешно（ от  Service)");

        } catch (Exception e) {
            AppLog.e(TAG, " от  Service ЗапускFeishuСервисОшибка", e);
        } finally {
            synchronized (feishuLock) {
                isFeishuStarting = false;
            }
        }
    }

    /**
     * 构建基本СтатусИнформация（不依赖 MainActivity)
     */
    private String buildBasicStatusInfo(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 EVCam Статус\n");
        sb.append("━━━━━━━━━━━━━━\n");

        try {
            AppConfig appConfig = new AppConfig(context);

            // УдалённыйСервисСтатус
            sb.append("🌐 УдалённыйСервис:\n");
            sb.append("• DingTalk: ").append(isDingTalkRunning() ? "Подключено" : "Не подключено").append("\n");
            sb.append("• Telegram: ").append(isTelegramRunning() ? "Подключено" : "Не подключено").append("\n");
            sb.append("• Feishu: ").append(isFeishuRunning() ? "Подключено" : "Не подключено").append("\n");

            // ХранилищеИнформация
            try {
                boolean useExternal = appConfig.isUsingExternalSdCard();
                java.io.File storageDir = useExternal ?
                        StorageHelper.getExternalSdCardRoot(context) :
                        android.os.Environment.getExternalStorageDirectory();
                if (storageDir != null && storageDir.exists()) {
                    long available = StorageHelper.getAvailableSpace(storageDir);
                    String availableStr = StorageHelper.formatSize(available);
                    sb.append("💾 Хранилище: ").append(useExternal ? "USB-накопитель" : "Внутреннее");
                    sb.append("(осталось ").append(availableStr).append(")\n");
                }
            } catch (Exception e) {
                // 忽略
            }

            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("💡 Отправьте команду для удалённой записи/фото");

        } catch (Exception e) {
            sb.append("Ошибка получения статуса: ").append(e.getMessage());
        }

        return sb.toString();
    }
}
