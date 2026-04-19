package com.kooo.evcam;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;

/**
 * Хелпер для установки APK через стандартный системный установщик.
 *
 * Поток:
 * 1. {@link #markPendingApk(Context, File)} — запоминаем путь к скачанному APK.
 * 2. {@link #installApk(Fragment, File)} — проверяем разрешение
 *    REQUEST_INSTALL_PACKAGES; если нет — открываем системные настройки.
 *    Если есть — запускаем стандартный диалог установки через FileProvider.
 * 3. После успешной установки система отправляет MY_PACKAGE_REPLACED, и
 *    {@link PackageReplacedReceiver} вызывает {@link #cleanupPendingApk(Context)}
 *    для удаления APK файла.
 * 4. {@link #cleanupOldApks(Context)} — вспомогательный метод, удаляет
 *    устаревшие EVCam_*.apk при следующем запуске на случай, если установка
 *    не дошла до конца (например, пользователь отменил).
 */
public final class ApkInstallHelper {
    private static final String TAG = "ApkInstallHelper";

    private static final String PREFS_NAME = "apk_install_prefs";
    private static final String KEY_PENDING_APK_PATH = "pending_apk_path";

    public static final int REQUEST_CODE_INSTALL_PERMISSION = 0xA1C;

    private ApkInstallHelper() {}

    /**
     * Сохраняем путь к APK, который сейчас будет ставиться,
     * чтобы удалить файл после успешной установки.
     */
    public static void markPendingApk(Context context, File apkFile) {
        if (context == null || apkFile == null) return;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_APK_PATH, apkFile.getAbsolutePath())
                .apply();
        AppLog.d(TAG, "Запомнен pending APK: " + apkFile.getAbsolutePath());
    }

    /**
     * Запускает стандартную установку APK. При отсутствии разрешения
     * REQUEST_INSTALL_PACKAGES открывает системный экран настроек.
     */
    public static void installApk(Fragment fragment, File apkFile) {
        if (fragment == null || fragment.getContext() == null || apkFile == null) {
            return;
        }
        Context context = fragment.getContext();

        if (!apkFile.exists()) {
            Toast.makeText(context, "APK файл не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 8.0+ требует явного разрешения на установку из неизвестных источников.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            requestInstallPermission(fragment);
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            AppLog.d(TAG, "Запущен установщик для: " + apkFile.getAbsolutePath());
        } catch (Exception e) {
            AppLog.e(TAG, "Ошибка запуска установщика APK", e);
            Toast.makeText(context,
                    "Не удалось запустить установку: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Открывает системные настройки для выдачи разрешения
     * "Установка из неизвестных источников" для нашего приложения.
     */
    private static void requestInstallPermission(Fragment fragment) {
        Context context = fragment.getContext();
        if (context == null) return;

        Toast.makeText(context,
                "Разрешите установку из неизвестных источников и нажмите «Проверить» снова",
                Toast.LENGTH_LONG).show();

        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            fragment.startActivityForResult(intent, REQUEST_CODE_INSTALL_PERMISSION);
        } catch (Exception e) {
            AppLog.e(TAG, "Не удалось открыть настройки разрешений", e);
            // Fallback — общий экран настроек источников.
            try {
                fragment.startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
                        REQUEST_CODE_INSTALL_PERMISSION);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Возвращает запомненный pending APK (если файл ещё существует),
     * иначе null. Используется чтобы возобновить установку после
     * выдачи разрешения REQUEST_INSTALL_PACKAGES.
     */
    public static File getPendingApk(Context context) {
        if (context == null) return null;
        String path = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_APK_PATH, null);
        if (path == null) return null;
        File f = new File(path);
        return f.exists() ? f : null;
    }

    /**
     * Удаляет APK, помеченный как pending. Вызывается из
     * {@link PackageReplacedReceiver} после успешной установки.
     */
    public static void cleanupPendingApk(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_PENDING_APK_PATH, null);
        prefs.edit().remove(KEY_PENDING_APK_PATH).apply();
        if (path == null) return;

        File apk = new File(path);
        if (apk.exists() && apk.delete()) {
            AppLog.d(TAG, "APK удалён после установки: " + path);
        } else {
            AppLog.d(TAG, "APK для удаления не найден или не удалось удалить: " + path);
        }
    }

    /**
     * Удаляет все ранее скачанные APK обновления EVCam_*.apk из папки Download.
     * Вызывается перед скачиванием новой версии и при старте приложения,
     * чтобы подчистить файлы, оставшиеся от прерванных или ручных установок.
     */
    public static void cleanupOldApks(Context context) {
        if (context == null) return;
        try {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir == null || !downloadDir.exists()) return;
            File[] files = downloadDir.listFiles((dir, name) ->
                    name != null && name.startsWith("EVCam_") && name.endsWith(".apk"));
            if (files == null) return;
            for (File f : files) {
                if (f.delete()) {
                    AppLog.d(TAG, "Удалён старый APK: " + f.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Ошибка очистки старых APK", e);
        }
    }
}
