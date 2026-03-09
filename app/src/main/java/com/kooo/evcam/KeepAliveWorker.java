package com.kooo.evcam;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * 定时保活задача
 * 每15 мин.выполнение一 раз，确保Приложение进程保持活跃
 */
public class KeepAliveWorker extends Worker {
    private static final String TAG = "KeepAliveWorker";

    public KeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppLog.d(TAG, "定时保活задачавыполнение - 确保Приложение进程活跃");

        try {
            // проверкаУдалённыйПросмотрСервисСтатус
            Context context = getApplicationContext();
            
            // 记录ТекущийРаботаСтатус
            AppLog.d(TAG, "Приложение进程保持活跃");
            AppLog.d(TAG, "无障碍СервисСтатус: " + (KeepAliveAccessibilityService.isRunning() ? "Работа" : "Не Работа"));
            
            // 可以 这里做一些轻量级 проверка，确保核心Сервиснормально
            // 例еслипроверкаDingTalkПодключениеСтатус等
            
            return Result.success();
        } catch (Exception e) {
            AppLog.e(TAG, "保活задачавыполнениеОшибка", e);
            return Result.retry();
        }
    }
}
