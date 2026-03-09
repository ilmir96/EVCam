package com.kooo.evcam.remote.upload;

import android.content.Context;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.FileTransferManager;
import com.kooo.evcam.StorageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 媒体Файл查找инструмент
 * 统一处理Видео/ФотоФайл 查找逻辑
 */
public class MediaFileFinder {
    private static final String TAG = "MediaFileFinder";
    
    private final Context context;
    
    public MediaFileFinder(Context context) {
        this.context = context;
    }
    
    /**
     * 查找ВидеоФайл
     * 优先 от временнокаталог查找，再查找最终каталог
     * 
     * @param timestamp Запись时间戳
     * @return ВидеоФайл列表，Если Не 找 до 返回空列表
     */
    public List<File> findVideoFiles(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            AppLog.e(TAG, "时间戳пусто，无法查找ВидеоФайл");
            return new ArrayList<>();
        }
        
        // 1. 优先 от временнокаталог查找
        File tempDir = new File(context.getCacheDir(), FileTransferManager.TEMP_VIDEO_DIR);
        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] tempFiles = tempDir.listFiles((dir, name) -> 
                name.endsWith(".mp4") && 
                name.startsWith(timestamp + "_") && 
                new File(dir, name).length() > 0
            );
            
            if (tempFiles != null && tempFiles.length > 0) {
                AppLog.d(TAG, " от временнокаталог找 до  " + tempFiles.length + " видеофайл(ов)");
                return new ArrayList<>(Arrays.asList(tempFiles));
            }
        }
        
        // 2.  от 最终каталог查找
        File videoDir = StorageHelper.getVideoDir(context);
        if (videoDir == null || !videoDir.exists()) {
            AppLog.e(TAG, "Видеокаталогне существует");
            return new ArrayList<>();
        }
        
        File[] files = videoDir.listFiles((dir, name) -> 
            name.startsWith(timestamp) && name.endsWith(".mp4")
        );
        
        if (files == null || files.length == 0) {
            AppLog.e(TAG, "Не найдены записанные видеофайлы，时间戳: " + timestamp);
            return new ArrayList<>();
        }
        
        AppLog.d(TAG, " от 最终каталог找 до  " + files.length + " видеофайл(ов)");
        return new ArrayList<>(Arrays.asList(files));
    }
    
    /**
     * 查找ВидеоФайл（поддержка多 шт.时间戳)
     * 用于 Watchdog 重建后查找所有Запись Файл
     * 
     * @param timestamps 所有Запись时间戳列表
     * @return ВидеоФайл列表，Если Не 找 до 返回空列表
     */
    public List<File> findVideoFiles(List<String> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            AppLog.e(TAG, "时间戳列表пусто，无法查找ВидеоФайл");
            return new ArrayList<>();
        }
        
        List<File> allFiles = new ArrayList<>();
        
        // 1.  от временнокаталог查找所有时间戳 应 Файл
        File tempDir = new File(context.getCacheDir(), FileTransferManager.TEMP_VIDEO_DIR);
        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] tempFiles = tempDir.listFiles((dir, name) -> {
                if (!name.endsWith(".mp4") || new File(dir, name).length() == 0) {
                    return false;
                }
                for (String ts : timestamps) {
                    if (name.startsWith(ts + "_")) {
                        return true;
                    }
                }
                return false;
            });
            
            if (tempFiles != null && tempFiles.length > 0) {
                allFiles.addAll(Arrays.asList(tempFiles));
                AppLog.d(TAG, " от временнокаталог找 до  " + tempFiles.length + " видеофайл(ов)");
            }
        }
        
        // 2.  от 最终каталог查找所有时间戳 应 Файл
        File videoDir = StorageHelper.getVideoDir(context);
        if (videoDir != null && videoDir.exists()) {
            File[] files = videoDir.listFiles((dir, name) -> {
                if (!name.endsWith(".mp4")) {
                    return false;
                }
                for (String ts : timestamps) {
                    if (name.startsWith(ts)) {
                        return true;
                    }
                }
                return false;
            });
            
            if (files != null && files.length > 0) {
                // 避免重复添加（временнокаталог и 最终каталог可能有同名Файл)
                for (File f : files) {
                    boolean exists = false;
                    for (File existing : allFiles) {
                        if (existing.getName().equals(f.getName())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        allFiles.add(f);
                    }
                }
                AppLog.d(TAG, " от 最终каталог额外找 до ВидеоФайл");
            }
        }
        
        if (allFiles.isEmpty()) {
            AppLog.e(TAG, "Не найдены записанные видеофайлы，时间戳: " + timestamps);
        } else {
            AppLog.d(TAG, "总Всего 找 до  " + allFiles.size() + " видеофайл(ов)（时间戳数: " + timestamps.size() + ")");
        }
        
        return allFiles;
    }
    
    /**
     * 查找ФотоФайл
     * 
     * @param timestamp Фото时间戳
     * @return ФотоФайл列表，Если Не 找 до 返回空列表
     */
    public List<File> findPhotoFiles(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            AppLog.e(TAG, "时间戳пусто，无法查找ФотоФайл");
            return new ArrayList<>();
        }
        
        File photoDir = StorageHelper.getPhotoDir(context);
        if (photoDir == null || !photoDir.exists()) {
            AppLog.e(TAG, "Фотокаталогне существует");
            return new ArrayList<>();
        }
        
        File[] files = photoDir.listFiles((dir, name) -> 
            name.startsWith(timestamp) && 
            (name.endsWith(".jpg") || name.endsWith(".jpeg"))
        );
        
        if (files == null || files.length == 0) {
            AppLog.e(TAG, "Не найдены сделанные фото，时间戳: " + timestamp);
            return new ArrayList<>();
        }
        
        AppLog.d(TAG, "找 до  " + files.length + " фото");
        return new ArrayList<>(Arrays.asList(files));
    }
    
    /**
     * 将временноФайл传输 до 最终каталог
     * 
     * @param tempFiles временноФайл列表
     */
    public void transferToFinalDir(List<File> tempFiles) {
        if (tempFiles == null || tempFiles.isEmpty()) {
            return;
        }
        
        // Получение最终Видеокаталог
        File videoDir = StorageHelper.getVideoDir(context);
        if (videoDir == null) {
            AppLog.e(TAG, "无法ПолучениеВидеокаталог，跳过Файл传输");
            return;
        }
        
        FileTransferManager transferManager = FileTransferManager.getInstance(context);
        for (File tempFile : tempFiles) {
            if (tempFile.exists()) {
                // 构造目标ФайлПуть
                File targetFile = new File(videoDir, tempFile.getName());
                
                transferManager.addTransferTask(tempFile, targetFile, new FileTransferManager.TransferCallback() {
                    @Override
                    public void onTransferComplete(File sourceFile, File targetFile) {
                        AppLog.d(TAG, "Файл传输завершение: " + sourceFile.getName() + " -> " + targetFile.getAbsolutePath());
                    }
                    
                    @Override
                    public void onTransferFailed(File sourceFile, File targetFile, String error) {
                        AppLog.e(TAG, "Файл传输Ошибка: " + sourceFile.getName() + ", Ошибка: " + error);
                    }
                });
            }
        }
    }
}
