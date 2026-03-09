package com.kooo.evcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * 保活广播接收器（增强版)
 * 
 * 策略：广撒网，只要Система有任何动静唤醒（参考 Macrodroid)
 * 
 * 监听 广播类型：
 * 
 * 【屏幕相Выкл】- 车机点火必亮屏，最稳 触发信号
 * - SCREEN_ON: 屏幕亮起
 * - SCREEN_OFF: 屏幕Закрыто
 * - USER_PRESENT: 用户解锁
 * 
 * 【电源相Выкл】- 车机点火必通电，非常可靠
 * - ACTION_POWER_CONNECTED: 电源接通
 * - ACTION_POWER_DISCONNECTED: 电源отключено
 * - BATTERY_CHANGED/LOW/OKAY: 电池Статус
 * 
 * 【蓝牙相Выкл】- 车机Запуск会автоматически连Телефон蓝牙
 * - STATE_CHANGED: 蓝牙Вкл/Выкл
 * - CONNECTION_STATE_CHANGED: 蓝牙ПодключениеСтатус
 * - ACL_CONNECTED/DISCONNECTED: Подключение BT-устройства/отключено
 * 
 * 【USB/Хранилище相Выкл】- 插USB-накопитель触发
 * - MEDIA_MOUNTED/UNMOUNTED: Монтирование хранилища/卸载
 * - USB_DEVICE_ATTACHED/DETACHED: USB设备
 * 
 * 【Сеть相Выкл】
 * - CONNECTIVITY_CHANGE: СетьСтатус变化
 * - WIFI_STATE_CHANGE: WiFiСтатус
 * 
 * 【Другое】
 * - TIME_TICK: 每 мин.触发（需动态注册)
 * - TIMEZONE_CHANGED/TIME_SET: Изменение времени
 * - LOCALE_CHANGED: Изменение языка
 * - AIRPLANE_MODE: Режим полёта
 * - HEADSET_PLUG: Наушники
 * - MY_PACKAGE_REPLACED: Приложениеобновление后重新激活
 */
public class KeepAliveReceiver extends BroadcastReceiver {
    private static final String TAG = "KeepAliveReceiver";
    
    // 自定义广播 Action，用于вручную触发保活проверка
    public static final String ACTION_KEEP_ALIVE = "com.kooo.evcam.ACTION_KEEP_ALIVE";
    
    private static KeepAliveReceiver timeTickReceiver;
    private static boolean isTimeTickRegistered = false;
    
    //  раз触发时间，用于防止短时间内重复触发
    private static long lastTriggerTime = 0;
    private static final long MIN_TRIGGER_INTERVAL = 3000; // минимум触发间隔 3  сек.

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        
        String action = intent.getAction();
        
        // 保活функция改为始终Вкл启（车机必需)
        
        switch (action) {
            // ========== 屏幕相Выкл（车机点火必亮屏，最稳触发) ==========
            case Intent.ACTION_SCREEN_ON:
                AppLog.d(TAG, "【屏幕】屏幕亮起（点火信号)");
                ensureServicesRunning(context, "Экран включён");
                break;
                
            case Intent.ACTION_SCREEN_OFF:
                AppLog.d(TAG, "【屏幕】屏幕Закрыто（熄火/息屏)");
                ensureServicesRunning(context, "Экран выключен");
                break;
                
            case Intent.ACTION_USER_PRESENT:
                AppLog.d(TAG, "【屏幕】用户解锁屏幕");
                ensureServicesRunning(context, "Устройство разблокировано");
                break;
                
            // ========== 电源相Выкл（车机点火必通电) ==========
            case Intent.ACTION_POWER_CONNECTED:
                AppLog.d(TAG, "【电源】电源接通（点火信号)");
                ensureServicesRunning(context, "Питание подключено");
                break;
                
            case Intent.ACTION_POWER_DISCONNECTED:
                AppLog.d(TAG, "【电源】电源отключено（熄火信号)");
                ensureServicesRunning(context, "Питание отключено");
                break;
                
            case Intent.ACTION_BATTERY_CHANGED:
                // 电池Статус变化频繁，静默处理
                ensureServicesRunningQuiet(context);
                break;
                
            case Intent.ACTION_BATTERY_LOW:
                AppLog.d(TAG, "【电源】Низкий заряд");
                ensureServicesRunning(context, "Низкий заряд");
                break;
                
            case Intent.ACTION_BATTERY_OKAY:
                AppLog.d(TAG, "【电源】电量Восстановлениенормально");
                ensureServicesRunning(context, "Заряд в норме");
                break;
                
            // ========== 蓝牙相Выкл（车机Запуск会автоматически连蓝牙) ==========
            case "android.bluetooth.adapter.action.STATE_CHANGED":
                AppLog.d(TAG, "【蓝牙】蓝牙Статус改变");
                ensureServicesRunning(context, "Изменение Bluetooth");
                break;
                
            case "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED":
                AppLog.d(TAG, "【蓝牙】蓝牙ПодключениеСтатус改变");
                ensureServicesRunning(context, "Изменение соединения BT");
                break;
                
            case "android.bluetooth.device.action.ACL_CONNECTED":
                AppLog.d(TAG, "【蓝牙】蓝牙设备Подключено");
                ensureServicesRunning(context, "Подключение BT-устройства");
                break;
                
            case "android.bluetooth.device.action.ACL_DISCONNECTED":
                AppLog.d(TAG, "【蓝牙】Отключение BT-устройства");
                ensureServicesRunning(context, "Отключение BT-устройства");
                break;
                
            // ========== USB/Хранилище相Выкл（插USB-накопитель触发) ==========
            case Intent.ACTION_MEDIA_MOUNTED:
                AppLog.d(TAG, "【Хранилище】Монтирование хранилища（USB-накопитель/SD卡插入)");
                ensureServicesRunning(context, "Монтирование хранилища");
                break;
                
            case Intent.ACTION_MEDIA_UNMOUNTED:
                AppLog.d(TAG, "【Хранилище】Размонтирование хранилища");
                ensureServicesRunning(context, "Размонтирование хранилища");
                break;
                
            case Intent.ACTION_MEDIA_REMOVED:
                AppLog.d(TAG, "【Хранилище】Извлечение хранилища");
                ensureServicesRunning(context, "Извлечение хранилища");
                break;
                
            case Intent.ACTION_MEDIA_EJECT:
                AppLog.d(TAG, "【Хранилище】Безопасное извлечение求");
                ensureServicesRunning(context, "Безопасное извлечение");
                break;
                
            case "android.hardware.usb.action.USB_DEVICE_ATTACHED":
                AppLog.d(TAG, "【USB】USB设备Подключено");
                ensureServicesRunning(context, "USBПодключение");
                break;
                
            case "android.hardware.usb.action.USB_DEVICE_DETACHED":
                AppLog.d(TAG, "【USB】USB设备отключено");
                ensureServicesRunning(context, "USBотключено");
                break;
                
            // ========== Сеть相Выкл ==========
            case "android.net.conn.CONNECTIVITY_CHANGE":
                AppLog.d(TAG, "【Сеть】СетьСтатус变化");
                ensureServicesRunning(context, "Изменение сети");
                break;
                
            case "android.net.wifi.STATE_CHANGE":
                AppLog.d(TAG, "【Сеть】WiFiСтатус变化");
                ensureServicesRunning(context, "Изменение WiFi");
                break;
                
            case "android.net.wifi.SCAN_RESULTS":
                // WiFi扫描结果，静默处理
                ensureServicesRunningQuiet(context);
                break;
                
            // ========== 音频相Выкл ==========
            case Intent.ACTION_HEADSET_PLUG:
                AppLog.d(TAG, "【音频】Наушники");
                ensureServicesRunning(context, "Наушники");
                break;
                
            case "android.media.AUDIO_BECOMING_NOISY":
                AppLog.d(TAG, "【音频】音频输出设备变化");
                ensureServicesRunning(context, "Изменение аудио");
                break;
                
            // ========== 时间/时区相Выкл ==========
            case Intent.ACTION_TIMEZONE_CHANGED:
                AppLog.d(TAG, "【时间】Изменение часового пояса");
                ensureServicesRunning(context, "Изменение часового пояса");
                break;
                
            case Intent.ACTION_TIME_CHANGED:
                AppLog.d(TAG, "【时间】时间Настройки变化");
                ensureServicesRunning(context, "Изменение времени");
                break;
                
            case Intent.ACTION_DATE_CHANGED:
                AppLog.d(TAG, "【时间】Изменение даты（跨天)");
                ensureServicesRunning(context, "Изменение даты");
                break;
                
            // ========== Системаконфигурация相Выкл ==========
            case Intent.ACTION_LOCALE_CHANGED:
                AppLog.d(TAG, "【Система】语言/区域变化");
                ensureServicesRunning(context, "Изменение языка");
                break;
                
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                AppLog.d(TAG, "【Система】Режим полёта切换");
                ensureServicesRunning(context, "Режим полёта");
                break;
                
            // ========== Приложение相Выкл ==========
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                AppLog.d(TAG, "【Приложение】Приложениеобновление，重新激活Сервис");
                ensureServicesRunning(context, "Приложениеобновление");
                // Приложениеобновление后重新注册 TIME_TICK
                registerTimeTick(context);
                break;
                
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_PACKAGE_REPLACED:
                // ДругоеПриложениеустановка/обновление，静默处理
                ensureServicesRunningQuiet(context);
                break;
                
            // ========== 每 мин.定时 ==========
            case Intent.ACTION_TIME_TICK:
                onTimeTick(context);
                break;
                
            // ========== 自定义保活广播 ==========
            case ACTION_KEEP_ALIVE:
                AppLog.d(TAG, "【保活】Получена команда: Ручное поддержаниепроверка求");
                ensureServicesRunning(context, "Ручное поддержание");
                break;
                
            default:
                // ДругоеНеизвестно广播также触发保活проверка
                AppLog.d(TAG, "【Другое】Получена команда: 广播: " + action);
                ensureServicesRunningQuiet(context);
                break;
        }
    }
    
    /**
     * TIME_TICK 处理（每 мин.调用)
     * использование轻量级проверка，避免频繁операция
     */
    private void onTimeTick(Context context) {
        // проверка无障碍СервисСтатус
        boolean accessibilityRunning = KeepAliveAccessibilityService.isRunning();
        
        if (accessibilityRunning) {
            // 无障碍СервисРабота，只需简单 д.志
            long runningMinutes = KeepAliveAccessibilityService.getRunningMinutes();
            if (runningMinutes % 5 == 0) {  // 每5 мин.输出一 раз详细 д.志
                AppLog.d(TAG, "【定时】无障碍СервисРабота " + runningMinutes + "  мин.");
            }
        } else {
            // 无障碍СервисНе Работа，попытка拉起Передний планСервис
            AppLog.d(TAG, "【定时】无障碍СервисНе Работа，попытка拉起Передний планСервис");
            ensureServicesRunning(context, "Плановая проверка");
        }
    }
    
    /**
     * 确保所有保活СервисВыполняется Работа（带触发原因)
     * @param context 文
     * @param reason 触发原因（用于Уведомление显示)
     */
    private void ensureServicesRunning(Context context, String reason) {
        // 防止短时间内重复触发
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < MIN_TRIGGER_INTERVAL) {
            return;
        }
        lastTriggerTime = now;
        
        try {
            // ЗапускПередний планСервис
            CameraForegroundService.start(context, "EVCam работает в фоне", "Причина: " + reason);
            AppLog.d(TAG, "求ЗапускПередний планСервис (Причина: " + reason + ")");
        } catch (Exception e) {
            AppLog.e(TAG, "ЗапускСервисОшибка: " + e.getMessage(), e);
        }
    }
    
    /**
     * 静默确保СервисРабота（不输出 д.志，用于频繁触发 广播)
     * @param context 文
     */
    private void ensureServicesRunningQuiet(Context context) {
        // 防止短时间内重复触发
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < MIN_TRIGGER_INTERVAL) {
            return;
        }
        lastTriggerTime = now;
        
        try {
            // ЗапускПередний планСервис
            CameraForegroundService.start(context, "EVCam работает в фоне", "Нажмите для возврата");
        } catch (Exception e) {
            // 静默Ошибка，不输出 д.志
        }
    }
    
    /**
     * 动态注册 TIME_TICK 广播接收器
     * TIME_TICK   Android 8.0+ 只能动态注册
     * 
     * @param context 文
     */
    public static synchronized void registerTimeTick(Context context) {
        if (isTimeTickRegistered) {
            AppLog.d(TAG, "TIME_TICK 接收器注册，跳过");
            return;
        }
        
        try {
            timeTickReceiver = new KeepAliveReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getApplicationContext().registerReceiver(
                        timeTickReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.getApplicationContext().registerReceiver(timeTickReceiver, filter);
            }
            
            isTimeTickRegistered = true;
            AppLog.d(TAG, "TIME_TICK 接收器动态注册（每 мин.触发)");
        } catch (Exception e) {
            AppLog.e(TAG, "注册 TIME_TICK 接收器Ошибка: " + e.getMessage(), e);
        }
    }
    
    /**
     * 注销 TIME_TICK 广播接收器
     * 
     * @param context 文
     */
    public static synchronized void unregisterTimeTick(Context context) {
        if (!isTimeTickRegistered || timeTickReceiver == null) {
            return;
        }
        
        try {
            context.getApplicationContext().unregisterReceiver(timeTickReceiver);
            timeTickReceiver = null;
            isTimeTickRegistered = false;
            AppLog.d(TAG, "TIME_TICK 接收器注销");
        } catch (Exception e) {
            AppLog.e(TAG, "注销 TIME_TICK 接收器Ошибка: " + e.getMessage(), e);
        }
    }
    
    /**
     * проверка TIME_TICK  否注册
     */
    public static boolean isTimeTickRegistered() {
        return isTimeTickRegistered;
    }
    
    /**
     * Отправка保活проверка广播
     * 可以 任何地方调用此方法触发保活проверка
     * 
     * @param context 文
     */
    public static void sendKeepAliveCheck(Context context) {
        try {
            Intent intent = new Intent(ACTION_KEEP_ALIVE);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception e) {
            AppLog.e(TAG, "Отправка保活проверка广播Ошибка: " + e.getMessage(), e);
        }
    }
}
