package com.kooo.evcam;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * GalaxyE5（E245)Система白名单конфигурация助手
 * 
 * 将 EVCam 添加 до 车机Система 三 шт.白名单конфигурацияФайл：
 * 1. geely_lifectl_start_list.xml - СистемаЗапуск列表
 * 2. ecarx_str_policies.xml - Ecarx STR 白名单
 * 3. bgms_config.xml - BGMS Фоновый режимуправление白名单
 * 
 * 通过 ADB TCP 协议（localhost:5555)выполнение， и "一键ПолучениеРазрешение"использование相同 通道。
 */
public class SystemWhitelistHelper {

    private static final String TAG = "SystemWhitelistHelper";
    private static final String SCRIPT_ASSET_NAME = "add_evcam_config.sh";
    private static final String RESTORE_SCRIPT_ASSET_NAME = "restore_evcam_config.sh";

    private final Context context;
    private AdbPermissionHelper adbHelper;

    /**
     * 回调接口， и  AdbPermissionHelper.Callback 一致
     */
    public interface Callback {
        /** 实时 д.志输出（ 主线程回调) */
        void onLog(String message);
        /** выполнениезавершение（ 主线程回调) */
        void onComplete(boolean success);
    }

    public SystemWhitelistHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * выполнение白名单конфигурация。
     * 1. 将脚本 от  assets 复制 до Приложение缓存каталог
     * 2. 通过 ADB TCP 协议выполнение脚本（ и 一键ПолучениеРазрешение相同 通道)
     * 3. 实时输出脚本 д.志
     */
    public void executeWhitelistSetup(Callback callback) {
        // 步骤 1：将脚本 от  assets 复制 до 缓存каталог
        callback.onLog("[INFO] Подготовка скрипта...");

        File scriptFile = copyScriptFromAssets(SCRIPT_ASSET_NAME);
        if (scriptFile == null) {
            callback.onLog("[ERROR] Не удалось подготовить скрипт");
            callback.onComplete(false);
            return;
        }
        callback.onLog("[OK] Скрипт готов: " + scriptFile.getAbsolutePath());
        callback.onLog("");

        // 步骤 2：通过 ADB выполнение脚本
        if (adbHelper == null) {
            adbHelper = new AdbPermissionHelper(context);
        }

        // ПриложениеВнутреннееПуть /data/data/...   ADB shell 可见，直接использование
        adbHelper.executeScriptFile(scriptFile.getAbsolutePath(), new AdbPermissionHelper.Callback() {
            @Override
            public void onLog(String message) {
                callback.onLog(message);
            }

            @Override
            public void onComplete(boolean allSuccess) {
                // Очистка временноФайл
                if (scriptFile.exists()) {
                    scriptFile.delete();
                }
                callback.onComplete(allSuccess);
            }
        });
    }

    /**
     * выполнение白名单Восстановление。
     * 1. 将Восстановление脚本 от  assets 复制 до Приложение缓存каталог
     * 2. 通过 ADB TCP 协议выполнение脚本
     * 3. 实时输出脚本 д.志
     */
    public void executeWhitelistRestore(Callback callback) {
        callback.onLog("[INFO] Подготовка скрипта восстановления...");

        File scriptFile = copyScriptFromAssets(RESTORE_SCRIPT_ASSET_NAME);
        if (scriptFile == null) {
            callback.onLog("[ERROR] Не удалось подготовить скрипт восстановления");
            callback.onComplete(false);
            return;
        }
        callback.onLog("[OK] Скрипт готов: " + scriptFile.getAbsolutePath());
        callback.onLog("");

        if (adbHelper == null) {
            adbHelper = new AdbPermissionHelper(context);
        }

        adbHelper.executeScriptFile(scriptFile.getAbsolutePath(), new AdbPermissionHelper.Callback() {
            @Override
            public void onLog(String message) {
                callback.onLog(message);
            }

            @Override
            public void onComplete(boolean allSuccess) {
                if (scriptFile.exists()) {
                    scriptFile.delete();
                }
                callback.onComplete(allSuccess);
            }
        });
    }

    /**
     * 将脚本Файл от  assets 复制 до Приложение缓存каталог
     */
    private File copyScriptFromAssets(String assetName) {
        File cacheDir = context.getCacheDir();
        File scriptFile = new File(cacheDir, assetName);

        try (InputStream is = context.getAssets().open(assetName);
             OutputStream os = new FileOutputStream(scriptFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();

            scriptFile.setReadable(true, false);
            return scriptFile;

        } catch (IOException e) {
            AppLog.e(TAG, "复制脚本ФайлОшибка", e);
            return null;
        }
    }
}
