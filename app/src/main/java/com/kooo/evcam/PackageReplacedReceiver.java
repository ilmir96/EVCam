package com.kooo.evcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Получает MY_PACKAGE_REPLACED после успешной установки нового APK
 * и удаляет файл-инсталлятор из папки Downloads.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {
    private static final String TAG = "PackageReplacedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;

        AppLog.d(TAG, "MY_PACKAGE_REPLACED — чистим установочный APK");
        ApkInstallHelper.cleanupPendingApk(context);
        // На всякий случай чистим возможные остатки от прерванных обновлений.
        ApkInstallHelper.cleanupOldApks(context);
    }
}
