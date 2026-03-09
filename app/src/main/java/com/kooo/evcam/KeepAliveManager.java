package com.kooo.evcam;

import android.content.Context;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * 保活управление器
 * управление WorkManager 定时保活задача
 */
public class KeepAliveManager {
    private static final String TAG = "KeepAliveManager";
    private static final String KEEP_ALIVE_WORK_NAME = "keep_alive_work";

    /**
     * Запуск定时保活задача
     * 每15 мин.выполнение一 раз（Android WorkManager минимум间隔)
     */
    public static void startKeepAliveWork(Context context) {
        AppLog.d(TAG, "Запуск定时保活задача（每15 мин.)");

        // 创建周期性задача求
        PeriodicWorkRequest keepAliveWork = new PeriodicWorkRequest.Builder(
                KeepAliveWorker.class,
                15, // минимум间隔15 мин.
                TimeUnit.MINUTES
        ).build();

        // использование KEEP 策略：Если существует，保持现有задача
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                KEEP_ALIVE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                keepAliveWork
        );

        AppLog.d(TAG, "定时保活задачаЗапущено");
    }

    /**
     * Остановка定时保活задача
     */
    public static void stopKeepAliveWork(Context context) {
        AppLog.d(TAG, "Остановка定时保活задача");
        WorkManager.getInstance(context).cancelUniqueWork(KEEP_ALIVE_WORK_NAME);
    }

    /**
     * проверка保活задача 否Выполняется Работа
     */
    public static boolean isKeepAliveWorkRunning(Context context) {
        try {
            return WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(KEEP_ALIVE_WORK_NAME)
                    .get()
                    .stream()
                    .anyMatch(workInfo -> !workInfo.getState().isFinished());
        } catch (Exception e) {
            AppLog.e(TAG, "проверка保活задачаСтатусОшибка", e);
            return false;
        }
    }
}
