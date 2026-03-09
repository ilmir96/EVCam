package com.kooo.evcam;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ХранилищеОчистка управление器
 * автоматически删除超过限制 旧Видео и ИзображениеФайл
 * 
 * функция：
 * - 冷Запуск30 сек.后выполнение首 раз检测
 * - 每隔1小时выполнение定期检测
 * - поддержка分别НастройкиВидео и Изображение Хранилище限制（GB)
 * - 删除时额外删除20%，避免频繁删除
 */
public class StorageCleanupManager {
    private static final String TAG = "StorageCleanupManager";
    
    // 定时задача延迟
    private static final long INITIAL_DELAY_MS = 30 * 1000;  // 冷Запуск后30 сек.
    private static final long PERIODIC_INTERVAL_MS = 60 * 60 * 1000;  // 每1小时
    
    // 额外删除比例（20%)
    private static final double EXTRA_DELETE_RATIO = 0.20;
    
    // GB 转 字节
    private static final long GB_TO_BYTES = 1024L * 1024L * 1024L;
    
    // Внутренняя памятьНизкий空间阈值（3GB)
    private static final long LOW_SPACE_THRESHOLD_BYTES = 3L * GB_TO_BYTES;
    
    // Низкий空间强制Очистка 比例（删除20% 用空间，保留80%)
    private static final double LOW_SPACE_CLEANUP_RATIO = 0.20;
    
    private final Context context;
    private final AppConfig appConfig;
    private ScheduledExecutorService scheduler;
    private Handler mainHandler;
    private boolean isRunning = false;
    
    public StorageCleanupManager(Context context) {
        this.context = context.getApplicationContext();
        this.appConfig = new AppConfig(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * ЗапускХранилищеОчистка задача
     * 冷Запуск30 сек.后выполнение首 раз检测，после每隔1小时выполнение一 раз
     * 注意：т.е.使Очистка функцияНе Включить，также会Запуск以检测Внутренняя памятьНизкий空间情况
     */
    public void start() {
        if (isRunning) {
            AppLog.d(TAG, "ХранилищеОчистка задача Работа");
            return;
        }
        
        isRunning = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // 30 сек.后выполнение首 раз检测
        scheduler.schedule(this::performCleanup, INITIAL_DELAY_MS, TimeUnit.MILLISECONDS);
        
        // 每1小时выполнение一 раз定期检测
        scheduler.scheduleAtFixedRate(
            this::performCleanup,
            INITIAL_DELAY_MS + PERIODIC_INTERVAL_MS,  // 首 раз定期检测 首 раз检测后1小时
            PERIODIC_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        AppLog.d(TAG, "ХранилищеОчистка задачаЗапущено：30 сек.后首 раз检测，после每1小时检测一 раз");
        AppLog.d(TAG, "Видео限制: " + appConfig.getVideoStorageLimitGb() + " GB, Изображение限制: " + appConfig.getPhotoStorageLimitGb() + " GB");
    }
    
    /**
     * ОстановкаХранилищеОчистка задача
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
        }
        isRunning = false;
        AppLog.d(TAG, "ХранилищеОчистка задачаОстановлено");
    }
    
    /**
     * выполнениеОчистка задача
     */
    private void performCleanup() {
        AppLog.d(TAG, "Вкл始выполнениеХранилищеОчистка 检测...");
        
        // 首先检测Внутренняя памятьНизкий空间情况（强制Очистка )
        performLowSpaceCleanupIfNeeded();
        
        int videoLimitGb = appConfig.getVideoStorageLimitGb();
        int photoLimitGb = appConfig.getPhotoStorageLimitGb();
        
        // 检测并Очистка Видео
        if (videoLimitGb > 0) {
            CleanupResult videoResult = cleanupDirectory(
                StorageHelper.getVideoDir(context),
                videoLimitGb * GB_TO_BYTES,
                "Видео"
            );
            if (videoResult.deletedCount > 0) {
                showCleanupNotification(videoResult, "Видео");
            }
        }
        
        // 检测并Очистка Изображение
        if (photoLimitGb > 0) {
            CleanupResult photoResult = cleanupDirectory(
                StorageHelper.getPhotoDir(context),
                photoLimitGb * GB_TO_BYTES,
                "Фото"
            );
            if (photoResult.deletedCount > 0) {
                showCleanupNotification(photoResult, "Фото");
            }
        }
        
        AppLog.d(TAG, "ХранилищеОчистка 检测завершение");
    }
    
    /**
     * Внутренняя памятьНизкий空间时强制Очистка 
     * 当использованиеВнутренняя память且Доступно空间ниже3GB时，强制Очистка 20% 用空间
     */
    private void performLowSpaceCleanupIfNeeded() {
        // 检测Текущий 否использованиеВнутренняя память
        // custom path 不应被当作internal处理
        if (appConfig.isUsingCustomPath()) {
            return;
        }
        boolean usingInternal = !appConfig.isUsingExternalSdCard() || StorageHelper.isSdCardFallback(context);

        if (!usingInternal) {
            // использованиеUSB-накопитель，不необходимо强制Очистка
            return;
        }
        
        // ПолучениеВнутренняя памятьДоступно空间
        File internalDir = android.os.Environment.getExternalStorageDirectory();
        long availableSpace = StorageHelper.getAvailableSpace(internalDir);
        
        AppLog.d(TAG, "Внутренняя памятьДоступно空间: " + StorageHelper.formatSize(availableSpace));
        
        if (availableSpace < 0 || availableSpace >= LOW_SPACE_THRESHOLD_BYTES) {
            // 空间充足，不необходимоОчистка 
            return;
        }
        
        AppLog.w(TAG, "Внутренняя память空间不足（<3GB)，Вкл始强制Очистка ...");
        
        // 强制Очистка Видео（删除20% 用空间)
        File videoDir = StorageHelper.getVideoDir(context, false);
        CleanupResult videoResult = cleanupByPercentage(videoDir, LOW_SPACE_CLEANUP_RATIO, "Видео");
        if (videoResult.deletedCount > 0) {
            showLowSpaceCleanupNotification(videoResult, "Видео");
        }
        
        // 强制Очистка Изображение（删除20% 用空间)
        File photoDir = StorageHelper.getPhotoDir(context, false);
        CleanupResult photoResult = cleanupByPercentage(photoDir, LOW_SPACE_CLEANUP_RATIO, "Фото");
        if (photoResult.deletedCount > 0) {
            showLowSpaceCleanupNotification(photoResult, "Фото");
        }
    }
    
    /**
     * 按比例Очистка каталог（删除指定比例 用空间)
     * @param directory 目标каталог
     * @param deleteRatio 删除比例（0.0-1.0)
     * @param typeName 类型名称
     * @return Очистка 结果
     */
    private CleanupResult cleanupByPercentage(File directory, double deleteRatio, String typeName) {
        CleanupResult result = new CleanupResult();
        
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return result;
        }
        
        File[] files = directory.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            return result;
        }
        
        // 计算Текущий总大小
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        
        result.originalSize = totalSize;
        
        if (totalSize == 0) {
            return result;
        }
        
        // 计算необходимо删除 大小（总大小 指定比例)
        long needToDelete = (long) (totalSize * deleteRatio);
        long targetSize = totalSize - needToDelete;
        
        AppLog.d(TAG, typeName + "强制Очистка ：Текущий占用 " + StorageHelper.formatSize(totalSize) + 
                ", будет удалено " + StorageHelper.formatSize(needToDelete) + " (20%)");
        
        // 按изменение时间排序（最旧  前)
        List<File> sortedFiles = new ArrayList<>(Arrays.asList(files));
        sortedFiles.sort(Comparator.comparingLong(File::lastModified));
        
        // 删除最旧 Файл直 до 达 до 目标大小
        long deletedSize = 0;
        int deletedCount = 0;
        
        for (File file : sortedFiles) {
            if (totalSize - deletedSize <= targetSize) {
                break;
            }
            
            long fileSize = file.length();
            if (file.delete()) {
                deletedSize += fileSize;
                deletedCount++;
                AppLog.d(TAG, "强制删除旧Файл: " + file.getName() + " (" + StorageHelper.formatSize(fileSize) + ")");
            }
        }
        
        result.deletedSize = deletedSize;
        result.deletedCount = deletedCount;
        result.finalSize = totalSize - deletedSize;
        
        AppLog.d(TAG, typeName + "强制Очистка завершение: удалено " + deletedCount + " файл(ов), освобождено " + StorageHelper.formatSize(deletedSize));
        
        return result;
    }
    
    /**
     * 显示Низкий空间强制Очистка Уведомление
     */
    private void showLowSpaceCleanupNotification(CleanupResult result, String typeName) {
        mainHandler.post(() -> {
            String message = "Недостаточно места, очистка " + typeName + " " + 
                    result.deletedCount + " шт.Файл（" + StorageHelper.formatSize(result.deletedSize) + ")";
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }
    
    /**
     * Очистка 指定каталог
     * @param directory 目标каталог
     * @param limitBytes 限制大小（字节)
     * @param typeName 类型名称（用于 д.志)
     * @return Очистка 结果
     */
    private CleanupResult cleanupDirectory(File directory, long limitBytes, String typeName) {
        CleanupResult result = new CleanupResult();
        
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            AppLog.w(TAG, typeName + "каталогне существует: " + (directory != null ? directory.getAbsolutePath() : "null"));
            return result;
        }
        
        // Получениекаталог所有Файл（不筛选格式)
        File[] files = directory.listFiles(File::isFile);
        
        if (files == null || files.length == 0) {
            AppLog.d(TAG, typeName + "каталогпусто");
            return result;
        }
        
        // 计算Текущий总大小
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        
        result.originalSize = totalSize;
        
        AppLog.d(TAG, typeName + "Текущий占用: " + StorageHelper.formatSize(totalSize) + 
                " / Лимит: " + StorageHelper.formatSize(limitBytes));
        
        // Если Не 超过限制，无需Очистка 
        if (totalSize <= limitBytes) {
            AppLog.d(TAG, typeName + "Не 超过限制，无需Очистка ");
            return result;
        }
        
        // 计算目标大小（限制 80%，т.е.额外删除20%)
        long targetSize = (long) (limitBytes * (1 - EXTRA_DELETE_RATIO));
        long needToDelete = totalSize - targetSize;
        
        AppLog.d(TAG, typeName + "超过限制，необходимо删除: " + StorageHelper.formatSize(needToDelete) + 
                ", целевой размер: " + StorageHelper.formatSize(targetSize));
        
        // 按изменение时间排序（最旧  前)
        List<File> sortedFiles = new ArrayList<>(Arrays.asList(files));
        sortedFiles.sort(Comparator.comparingLong(File::lastModified));
        
        // 删除最旧 Файл直 до 达 до 目标大小
        long deletedSize = 0;
        int deletedCount = 0;
        
        for (File file : sortedFiles) {
            if (totalSize - deletedSize <= targetSize) {
                break;
            }
            
            long fileSize = file.length();
            String fileName = file.getName();
            
            if (file.delete()) {
                deletedSize += fileSize;
                deletedCount++;
                AppLog.d(TAG, "Удалено" + typeName + ": " + fileName + " (" + StorageHelper.formatSize(fileSize) + ")");
            } else {
                AppLog.w(TAG, "Удалить" + typeName + "Ошибка: " + fileName);
            }
        }
        
        result.deletedCount = deletedCount;
        result.deletedSize = deletedSize;
        result.finalSize = totalSize - deletedSize;
        
        AppLog.d(TAG, typeName + "Очистка завершение: удалено " + deletedCount + " файл(ов), освобождено " + 
                StorageHelper.formatSize(deletedSize) + ", осталось " + StorageHelper.formatSize(result.finalSize));
        
        return result;
    }
    
    /**
     * 显示Очистка Уведомление
     */
    private void showCleanupNotification(CleanupResult result, String typeName) {
        mainHandler.post(() -> {
            String message = "Очистка " + typeName + ": удалено " + result.deletedCount + " файл(ов), освобождено " + 
                    StorageHelper.formatSize(result.deletedSize);
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            AppLog.d(TAG, "Очистка Уведомление: " + message);
        });
    }
    
    /**
     * вручную触发Очистка （用于тестированиеили用户вручнуюОчистка )
     */
    public void manualCleanup() {
        new Thread(this::performCleanup).start();
    }
    
    /**
     * ПолучениеТекущийВидео占用大小
     * @return 占用大小（字节)
     */
    public long getVideoUsedSize() {
        return getDirectorySize(StorageHelper.getVideoDir(context));
    }
    
    /**
     * ПолучениеТекущийИзображение占用大小
     * @return 占用大小（字节)
     */
    public long getPhotoUsedSize() {
        return getDirectorySize(StorageHelper.getPhotoDir(context));
    }
    
    /**
     * Получениекаталог所有Файл 总大小
     */
    private long getDirectorySize(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return 0;
        }
        
        File[] files = directory.listFiles(File::isFile);
        
        if (files == null) {
            return 0;
        }
        
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        return totalSize;
    }
    
    /**
     * Очистка 结果
     */
    private static class CleanupResult {
        long originalSize = 0;  // Очистка 前大小
        long deletedSize = 0;   // 删除 大小
        long finalSize = 0;     // Очистка 后大小
        int deletedCount = 0;   // 删除 Файл数
    }
}
