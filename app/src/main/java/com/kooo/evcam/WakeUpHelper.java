package com.kooo.evcam;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 唤醒инструмент类
 * 用于 Фоновый режимПолучена команда: DingTalkкоманда时保持CPUРабота并Запуск Activity
 * 注意：不会亮屏，поддержка息屏Статус静默Фото/Запись
 * 
 * 阻止休眠 Выкл键：
 * 1. PARTIAL_WAKE_LOCK - 保持 CPU Работа
 * 2. 电池优化白名单 - 防止 Doze режим忽略 WakeLock
 * 3.  Передний планСервис持有 WakeLock - 比 Activity 更可靠
 */
public class WakeUpHelper {
    private static final String TAG = "WakeUpHelper";

    // CPU唤醒锁（不亮屏)- 用于Удалённыйкоманда（有таймаут)
    private static PowerManager.WakeLock wakeLock;
    
    // 持续唤醒锁 - 用于防止休眠（无таймаут)
    private static PowerManager.WakeLock persistentWakeLock;

    /**
     * проверка 否有Разрешение плавающего окна（用于Фоновый режимЗапускActivity)
     * Android 10+ необходимо此Разрешение才能 от Фоновый режимЗапуск Activity
     */
    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    /**
     * 求Разрешение плавающего окна
     * необходимо用户вручную授权
     */
    public static void requestOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * ПолучениеCPU唤醒锁（不亮屏)
     * 确保 息屏СтатусCPU保持Работа，能够завершениеФото/Запись
     */
    public static void acquireCpuWakeLock(Context context) {
        AppLog.d(TAG, "Acquiring CPU wake lock (screen stays off)...");

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            AppLog.e(TAG, "PowerManager is null");
            return;
        }

        // 释放до 唤醒锁
        releaseWakeLock();

        // 创建新 唤醒锁
        // PARTIAL_WAKE_LOCK: 只保持CPUРабота，不亮屏
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "EVCam:RemoteCommand"
        );

        // 持有唤醒锁 5  мин.（足够завершениеФотоилиЗапись+传)
        wakeLock.acquire(5 * 60 * 1000);
        AppLog.d(TAG, "CPU WakeLock acquired for 5 minutes");
    }

    /**
     * 释放唤醒锁
     */
    public static void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                AppLog.d(TAG, "WakeLock released");
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to release WakeLock", e);
            }
        }
        wakeLock = null;
    }
    
    /**
     * Получение持续唤醒锁（防止Система休眠)
     * 用于необходимо长期保持CPUРабота 场景，если车机熄火后仍需接收Удалённый消息
     * 注意：会增加功耗，необходимо用户明确Вкл启
     */
    public static void acquirePersistentWakeLock(Context context) {
        AppLog.d(TAG, "Acquiring persistent wake lock (prevent sleep)...");

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            AppLog.e(TAG, "PowerManager is null");
            return;
        }

        // Если 经持有，不重复Получение
        if (persistentWakeLock != null && persistentWakeLock.isHeld()) {
            AppLog.d(TAG, "Persistent WakeLock already held");
            return;
        }

        // 创建持续唤醒锁
        // PARTIAL_WAKE_LOCK: 只保持CPUРабота，不亮屏
        persistentWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "EVCam:PreventSleep"
        );

        // 持有唤醒锁，不Настройкитаймаут（直 до вручную释放)
        persistentWakeLock.acquire();
        AppLog.d(TAG, "Persistent WakeLock acquired (no timeout) - system will not sleep");
    }
    
    /**
     * 释放持续唤醒锁
     */
    public static void releasePersistentWakeLock() {
        if (persistentWakeLock != null && persistentWakeLock.isHeld()) {
            try {
                persistentWakeLock.release();
                AppLog.d(TAG, "Persistent WakeLock released - system can sleep now");
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to release persistent WakeLock", e);
            }
        }
        persistentWakeLock = null;
    }
    
    /**
     * проверка持续唤醒锁 否 持有
     */
    public static boolean isPersistentWakeLockHeld() {
        return persistentWakeLock != null && persistentWakeLock.isHeld();
    }
    
    /**
     * проверкаПриложение 否 电池优化白名单
     * Android 6.0+   Doze режим会忽略 WakeLock，只有加入白名单才能真正阻止休眠
     * 
     * @return true 表示 白名单（不受 Doze 限制)
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return pm.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true; // Android 6.0 и ниже不необходимо
    }
    
    /**
     * 求加入电池优化白名单
     * 这 阻止休眠 Выкл键！Doze режим只有白名单Приложение  WakeLock 才действует
     * 
     * 注意：会弹出Система 话框，необходимо用户Подтвердить
     */
    public static void requestIgnoreBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    AppLog.d(TAG, "Requesting battery optimization whitelist");
                } catch (Exception e) {
                    AppLog.e(TAG, "Failed to request battery optimization whitelist", e);
                    // 某些设备可能не поддерживается，попыткаоткрыть电池优化Настройки页面
                    openBatteryOptimizationSettings(context);
                }
            } else {
                AppLog.d(TAG, "Already in battery optimization whitelist");
            }
        }
    }
    
    /**
     * открыть电池优化Настройки页面（备用方案)
     */
    public static void openBatteryOptimizationSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to open battery optimization settings", e);
        }
    }

    /**
     * Запуск MainActivity  до Передний план，并传递команда参数
     * 
     * @param context 文
     * @param action 动作类型: "record" или "photo"
     * @param conversationId DingTalk会话ID
     * @param conversationType DingTalk会话类型
     * @param userId DingTalk用户ID
     * @param duration Запись时长（только record 时действует)
     */
    public static void launchMainActivityWithCommand(Context context, String action,
            String conversationId, String conversationType, String userId, int duration) {
        
        AppLog.d(TAG, "Launching MainActivity with command: " + action);

        // ПолучениеCPU唤醒锁，确保息屏Статустакже能выполнение
        acquireCpuWakeLock(context);

        // 创建 Intent
        Intent intent = new Intent(context, MainActivity.class);
        
        // Настройки flags
        // FLAG_ACTIVITY_NEW_TASK:  от 非 Activity 文Запуск时必须
        // FLAG_ACTIVITY_CLEAR_TOP: Если  Activity существует，очистка其 所有 Activity
        // FLAG_ACTIVITY_SINGLE_TOP: Если  Activity  栈顶，不创建新实例，调用 onNewIntent
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 传递команда参数
        intent.putExtra("remote_action", action);
        intent.putExtra("remote_wake_up", true);  // 标记这  от Фоновый режим唤醒 
        intent.putExtra("remote_conversation_id", conversationId);
        intent.putExtra("remote_conversation_type", conversationType);
        intent.putExtra("remote_user_id", userId);
        intent.putExtra("remote_duration", duration);

        // Запуск Activity
        context.startActivity(intent);
        AppLog.d(TAG, "MainActivity launch intent sent");
    }

    /**
     * Запуск MainActivity выполнениеЗаписькоманда
     */
    public static void launchForRecording(Context context, String conversationId,
            String conversationType, String userId, int durationSeconds) {
        launchMainActivityWithCommand(context, "record", conversationId, conversationType, userId, durationSeconds);
    }

    /**
     * Запуск MainActivity выполнениеФотокоманда
     */
    public static void launchForPhoto(Context context, String conversationId,
            String conversationType, String userId) {
        launchMainActivityWithCommand(context, "photo", conversationId, conversationType, userId, 0);
    }

    /**
     * Запуск MainActivity выполнениеЗапускНепрерывная записькоманда（等同点击Запись按钮)
     */
    public static void launchForStartRecording(Context context) {
        launchMainActivityWithCommand(context, "start_recording", null, null, null, 0);
    }

    /**
     * Запуск MainActivity выполнениеОстановить записькоманда
     */
    public static void launchForStopRecording(Context context) {
        launchMainActivityWithCommand(context, "stop_recording", null, null, null, 0);
    }

    /**
     * Запуск MainActivity переключиться на передний план
     * 用于Удалённыйкоманда将Приложение带 до Передний план
     */
    public static void launchForForeground(Context context) {
        AppLog.d(TAG, "Launching MainActivity to foreground");
        
        // ПолучениеCPU唤醒锁
        acquireCpuWakeLock(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 传递Передний планкоманда参数
        intent.putExtra("remote_action", "foreground");
        intent.putExtra("remote_wake_up", true);

        context.startActivity(intent);
        AppLog.d(TAG, "MainActivity launch intent sent for foreground");
    }

    /**
     * Отправка广播Уведомление MainActivity переключиться в фоновый режим
     * использование广播而不  startActivity，避免 Activity 闪烁 до Передний план
     */
    public static void sendBackgroundBroadcast(Context context) {
        AppLog.d(TAG, "Sending background broadcast to MainActivity");

        Intent intent = new Intent(ACTION_MOVE_TO_BACKGROUND);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
        
        AppLog.d(TAG, "Background broadcast sent");
    }
    
    /**
     * Фоновый режим切换广播 Action
     */
    public static final String ACTION_MOVE_TO_BACKGROUND = "com.kooo.evcam.ACTION_MOVE_TO_BACKGROUND";

    // ==================== Telegram 相Выкл方法 ====================

    /**
     * Запуск MainActivity выполнение Telegram Записькоманда
     * @param context 文
     * @param chatId Telegram Chat ID
     * @param durationSeconds Запись时长
     */
    public static void launchForRecordingTelegram(Context context, long chatId, int durationSeconds) {
        AppLog.d(TAG, "Launching MainActivity for Telegram recording: chatId=" + chatId + ", duration=" + durationSeconds);

        // ПолучениеCPU唤醒锁
        acquireCpuWakeLock(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 传递 Telegram команда参数
        intent.putExtra("remote_action", "record");
        intent.putExtra("remote_wake_up", true);  // 标记这  от Фоновый режим唤醒 
        intent.putExtra("remote_source", "telegram");
        intent.putExtra("telegram_chat_id", chatId);
        intent.putExtra("remote_duration", durationSeconds);

        context.startActivity(intent);
        AppLog.d(TAG, "MainActivity launch intent sent for Telegram");
    }

    /**
     * Запуск MainActivity выполнение Telegram Фотокоманда
     * @param context 文
     * @param chatId Telegram Chat ID
     */
    public static void launchForPhotoTelegram(Context context, long chatId) {
        AppLog.d(TAG, "Launching MainActivity for Telegram photo: chatId=" + chatId);

        // ПолучениеCPU唤醒锁
        acquireCpuWakeLock(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 传递 Telegram команда参数
        intent.putExtra("remote_action", "photo");
        intent.putExtra("remote_wake_up", true);  // 标记这  от Фоновый режим唤醒 
        intent.putExtra("remote_source", "telegram");
        intent.putExtra("telegram_chat_id", chatId);

        context.startActivity(intent);
        AppLog.d(TAG, "MainActivity launch intent sent for Telegram photo");
    }

    // ==================== Feishu相Выкл方法 ====================

    /**
     * Запуск MainActivity выполнениеFeishuЗаписькоманда
     * @param context 文
     * @param chatId Feishu会话 ID
     * @param messageId 消息 ID（用于回复)
     * @param durationSeconds Запись时长
     */
    public static void launchForRecordingFeishu(Context context, String chatId, String messageId, int durationSeconds) {
        AppLog.d(TAG, "Launching MainActivity for Feishu recording: chatId=" + chatId + ", duration=" + durationSeconds);

        // ПолучениеCPU唤醒锁
        acquireCpuWakeLock(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 传递Feishuкоманда参数
        intent.putExtra("remote_action", "record");
        intent.putExtra("remote_wake_up", true);  // 标记这  от Фоновый режим唤醒 
        intent.putExtra("remote_source", "feishu");
        intent.putExtra("feishu_chat_id", chatId);
        intent.putExtra("feishu_message_id", messageId);
        intent.putExtra("remote_duration", durationSeconds);

        context.startActivity(intent);
        AppLog.d(TAG, "MainActivity launch intent sent for Feishu recording");
    }

    /**
     * Запуск MainActivity выполнениеFeishuФотокоманда
     * @param context 文
     * @param chatId Feishu会话 ID
     * @param messageId 消息 ID（用于回复)
     */
    public static void launchForPhotoFeishu(Context context, String chatId, String messageId) {
        AppLog.d(TAG, "Launching MainActivity for Feishu photo: chatId=" + chatId);

        // ПолучениеCPU唤醒锁
        acquireCpuWakeLock(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 传递Feishuкоманда参数
        intent.putExtra("remote_action", "photo");
        intent.putExtra("remote_wake_up", true);  // 标记这  от Фоновый режим唤醒 
        intent.putExtra("remote_source", "feishu");
        intent.putExtra("feishu_chat_id", chatId);
        intent.putExtra("feishu_message_id", messageId);

        context.startActivity(intent);
        AppLog.d(TAG, "MainActivity launch intent sent for Feishu photo");
    }

}
