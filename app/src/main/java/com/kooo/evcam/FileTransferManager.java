package com.kooo.evcam;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Файл传输управление器
 * 负责将временнокаталог ВидеоФайл异步传输 до 目标Хранилище（еслиUSB-накопитель)
 * 
 * 工作原理：
 * 1. Запись时先写入Внутренняя память временнокаталог（Высокий速)
 * 2. 分завершение后，将Файл加入传输队列
 * 3. Фоновый режим线程负责将Файл移动/复制 до 目标каталог
 * 4. 传输завершение后删除временноФайл
 * 
 * 这样可以避免USB-накопитель慢速写入影响Запись性能
 */
public class FileTransferManager {
    private static final String TAG = "FileTransferManager";
    
    // временнокаталог名称（ Внутренняя память Приложение缓存каталог)
    public static final String TEMP_VIDEO_DIR = "temp_video";
    
    // 传输задача
    private static class TransferTask {
        final File sourceFile;      // 源Файл（временнокаталог)
        final File targetFile;      // 目标Файл（最终ХранилищеПозиция)
        final TransferCallback callback;
        int retryCount;             // 重试 раз数
        
        TransferTask(File source, File target, TransferCallback callback) {
            this.sourceFile = source;
            this.targetFile = target;
            this.callback = callback;
            this.retryCount = 0;
        }
    }
    
    // 传输回调
    public interface TransferCallback {
        void onTransferComplete(File sourceFile, File targetFile);
        void onTransferFailed(File sourceFile, File targetFile, String error);
    }
    
    // 单例
    private static FileTransferManager instance;
    
    private final Context context;
    private final ConcurrentLinkedQueue<TransferTask> transferQueue;
    private HandlerThread transferThread;
    private Handler transferHandler;
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isProcessing;
    
    // конфигурация
    private static final int MAX_RETRY_COUNT = 3;           // максимум重试 раз数
    private static final long RETRY_DELAY_MS = 5000;        // 重试延迟（毫 сек.)
    private static final long TRANSFER_CHECK_INTERVAL_MS = 1000;  // проверка队列间隔
    private static final long STARTUP_CLEANUP_DELAY_MS = 60 * 1000;  // Запуск后Очистка 延迟：1 мин.
    private static final long TEMP_FILE_EXPIRE_MS = 60 * 60 * 1000;  // временноФайлистекло时间：1小时
    
    // 统计
    private long totalTransferred = 0;      // 传输Файл数
    private long totalFailed = 0;           // ОшибкаФайл数
    private long totalBytesTransferred = 0; // 传输字节数
    
    private FileTransferManager(Context context) {
        this.context = context.getApplicationContext();
        this.transferQueue = new ConcurrentLinkedQueue<>();
        this.isRunning = new AtomicBoolean(false);
        this.isProcessing = new AtomicBoolean(false);
    }
    
    /**
     * Получение单例实例
     */
    public static synchronized FileTransferManager getInstance(Context context) {
        if (instance == null) {
            instance = new FileTransferManager(context);
        }
        return instance;
    }
    
    /**
     * Запуск传输Сервис
     */
    public void start() {
        if (isRunning.getAndSet(true)) {
            AppLog.d(TAG, "Transfer service already running");
            return;
        }
        
        transferThread = new HandlerThread("FileTransfer");
        transferThread.start();
        transferHandler = new Handler(transferThread.getLooper());
        
        // Запуск定期проверка队列
        scheduleNextCheck();
        
        // Запуск后1 мин.проверка并Очистка истекло временноФайл
        transferHandler.postDelayed(this::cleanupExpiredTempFiles, STARTUP_CLEANUP_DELAY_MS);
        
        AppLog.d(TAG, "File transfer service started");
    }
    
    /**
     * Остановка传输Сервис
     */
    public void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }
        
        if (transferHandler != null) {
            transferHandler.removeCallbacksAndMessages(null);
            transferHandler = null;
        }
        
        if (transferThread != null) {
            transferThread.quitSafely();
            try {
                transferThread.join(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            transferThread = null;
        }
        
        AppLog.d(TAG, "File transfer service stopped. Stats: transferred=" + totalTransferred + 
                ", failed=" + totalFailed + ", bytes=" + formatSize(totalBytesTransferred));
    }
    
    /**
     * 添加传输задача
     * @param sourceFile 源Файл（временнокаталог)
     * @param targetFile 目标Файл（最终Позиция)
     * @param callback 回调（可为null)
     */
    public void addTransferTask(File sourceFile, File targetFile, TransferCallback callback) {
        if (sourceFile == null || !sourceFile.exists()) {
            AppLog.w(TAG, "Source file does not exist: " + sourceFile);
            if (callback != null) {
                callback.onTransferFailed(sourceFile, targetFile, "Source file not found");
            }
            return;
        }
        
        TransferTask task = new TransferTask(sourceFile, targetFile, callback);
        transferQueue.offer(task);
        
        AppLog.d(TAG, "Added transfer task: " + sourceFile.getName() + " -> " + targetFile.getAbsolutePath());
        
        // Если Сервис Работа，立т.е.触发处理
        if (isRunning.get() && transferHandler != null) {
            transferHandler.post(this::processQueue);
        }
    }
    
    /**
     * ПолучениевременноВидеокаталог
     * @return временнокаталог，Если 创建Ошибка返回null
     */
    public File getTempVideoDir() {
        File tempDir = new File(context.getCacheDir(), TEMP_VIDEO_DIR);
        if (!tempDir.exists()) {
            if (!tempDir.mkdirs()) {
                AppLog.e(TAG, "Failed to create temp video directory: " + tempDir.getAbsolutePath());
                return null;
            }
        }
        return tempDir;
    }
    
    /**
     * Получениевременнокаталог Файл数量
     */
    public int getPendingFileCount() {
        File tempDir = getTempVideoDir();
        if (tempDir == null || !tempDir.exists()) {
            return 0;
        }
        File[] files = tempDir.listFiles();
        return files != null ? files.length : 0;
    }
    
    /**
     * Получениевременнокаталог占用 空间（字节)
     */
    public long getTempDirSize() {
        File tempDir = getTempVideoDir();
        if (tempDir == null || !tempDir.exists()) {
            return 0;
        }
        
        long size = 0;
        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
        return size;
    }
    
    /**
     * Очистка временнокаталог（删除所有Файл)
     * 注意：только Подтвердить不необходимо这些Файл时调用
     */
    public void clearTempDir() {
        File tempDir = getTempVideoDir();
        if (tempDir == null || !tempDir.exists()) {
            return;
        }
        
        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.delete()) {
                    AppLog.d(TAG, "Deleted temp file: " + file.getName());
                }
            }
        }
        
        AppLog.d(TAG, "Temp directory cleared");
    }
    
    /**
     * Получение队列ожидание传输 задача数
     */
    public int getQueueSize() {
        return transferQueue.size();
    }
    
    /**
     * Получение传输统计Информация
     */
    public String getStats() {
        return String.format("Передано: %d файлов (%s), ошибок: %d, в очереди: %d, временных: %d",
                totalTransferred, formatSize(totalBytesTransferred), 
                totalFailed, getQueueSize(), getPendingFileCount());
    }
    
    // ===== 私有方法 =====
    
    /**
     * 调度一 раз队列проверка
     */
    private void scheduleNextCheck() {
        if (!isRunning.get() || transferHandler == null) {
            return;
        }
        
        transferHandler.postDelayed(() -> {
            if (isRunning.get()) {
                processQueue();
                scheduleNextCheck();
            }
        }, TRANSFER_CHECK_INTERVAL_MS);
    }
    
    /**
     * Очистка истекло временноФайл
     *  СервисРабота期间定期调用
     */
    private void cleanupExpiredTempFiles() {
        File tempDir = getTempVideoDir();
        if (tempDir == null || !tempDir.exists()) {
            return;
        }
        
        File[] files = tempDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        
        long now = System.currentTimeMillis();
        int deletedCount = 0;
        long deletedSize = 0;
        
        for (File file : files) {
            long fileAge = now - file.lastModified();
            if (fileAge > TEMP_FILE_EXPIRE_MS) {
                long fileSize = file.length();
                if (file.delete()) {
                    deletedCount++;
                    deletedSize += fileSize;
                    AppLog.d(TAG, "Cleanup: deleted expired temp file: " + file.getName());
                }
            }
        }
        
        if (deletedCount > 0) {
            AppLog.d(TAG, "Periodic cleanup: deleted " + deletedCount + " expired files, freed " + formatSize(deletedSize));
        }
    }
    
    /**
     * 处理传输队列
     */
    private void processQueue() {
        if (!isRunning.get() || isProcessing.getAndSet(true)) {
            return;
        }
        
        try {
            TransferTask task;
            while ((task = transferQueue.poll()) != null) {
                if (!isRunning.get()) {
                    // СервисОстановка，将задача放回队列
                    transferQueue.offer(task);
                    break;
                }
                
                processTask(task);
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /**
     * 处理单 шт.传输задача
     */
    private void processTask(TransferTask task) {
        if (!task.sourceFile.exists()) {
            AppLog.w(TAG, "Source file no longer exists: " + task.sourceFile.getName());
            if (task.callback != null) {
                task.callback.onTransferFailed(task.sourceFile, task.targetFile, "Source file not found");
            }
            totalFailed++;
            return;
        }
        
        // 确保目标каталогсуществует
        File targetDir = task.targetFile.getParentFile();
        if (targetDir != null && !targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                AppLog.e(TAG, "Failed to create target directory: " + targetDir.getAbsolutePath());
                handleTransferFailure(task, "Cannot create target directory");
                return;
            }
        }
        
        // попытка移动Файл（Если  同一ФайлСистема，这 最快 )
        boolean moved = task.sourceFile.renameTo(task.targetFile);
        
        if (moved) {
            // 移动Успешно
            long fileSize = task.targetFile.length();
            AppLog.d(TAG, "File moved successfully: " + task.sourceFile.getName() + 
                    " -> " + task.targetFile.getAbsolutePath() + " (" + formatSize(fileSize) + ")");
            
            totalTransferred++;
            totalBytesTransferred += fileSize;
            
            if (task.callback != null) {
                task.callback.onTransferComplete(task.sourceFile, task.targetFile);
            }
        } else {
            // 移动Ошибка（可能跨ФайлСистема)，попытка复制
            AppLog.d(TAG, "Move failed, trying copy: " + task.sourceFile.getName());
            
            boolean copied = copyFile(task.sourceFile, task.targetFile);
            
            if (copied) {
                // 复制Успешно，删除源Файл
                long fileSize = task.targetFile.length();
                
                if (task.sourceFile.delete()) {
                    AppLog.d(TAG, "File copied and source deleted: " + task.sourceFile.getName() + 
                            " -> " + task.targetFile.getAbsolutePath() + " (" + formatSize(fileSize) + ")");
                } else {
                    AppLog.w(TAG, "File copied but failed to delete source: " + task.sourceFile.getName());
                }
                
                totalTransferred++;
                totalBytesTransferred += fileSize;
                
                if (task.callback != null) {
                    task.callback.onTransferComplete(task.sourceFile, task.targetFile);
                }
            } else {
                // 复制такжеОшибка
                handleTransferFailure(task, "Copy failed");
            }
        }
    }
    
    /**
     * 处理传输Ошибка
     */
    private void handleTransferFailure(TransferTask task, String error) {
        task.retryCount++;
        
        if (task.retryCount < MAX_RETRY_COUNT) {
            // 重试
            AppLog.w(TAG, "Transfer failed, will retry (" + task.retryCount + "/" + MAX_RETRY_COUNT + "): " + 
                    task.sourceFile.getName() + " - " + error);
            
            // 延迟后重新加入队列
            if (transferHandler != null) {
                transferHandler.postDelayed(() -> {
                    transferQueue.offer(task);
                }, RETRY_DELAY_MS);
            }
        } else {
            // 超过重试 раз数，放弃
            AppLog.e(TAG, "Transfer failed after " + MAX_RETRY_COUNT + " retries: " + 
                    task.sourceFile.getName() + " - " + error);
            
            totalFailed++;
            
            if (task.callback != null) {
                task.callback.onTransferFailed(task.sourceFile, task.targetFile, error);
            }
        }
    }
    
    /**
     * 复制Файл（использование NIO Channel，效率较Высокий)
     */
    private boolean copyFile(File source, File target) {
        FileChannel sourceChannel = null;
        FileChannel targetChannel = null;
        
        try {
            sourceChannel = new FileInputStream(source).getChannel();
            targetChannel = new FileOutputStream(target).getChannel();
            
            long size = sourceChannel.size();
            long transferred = 0;
            
            // 分块传输，避免内存问题
            final long CHUNK_SIZE = 8 * 1024 * 1024;  // 8MB chunks
            
            while (transferred < size) {
                long remaining = size - transferred;
                long toTransfer = Math.min(remaining, CHUNK_SIZE);
                long actualTransferred = sourceChannel.transferTo(transferred, toTransfer, targetChannel);
                
                if (actualTransferred == 0) {
                    // 传输停滞，可能有问题
                    AppLog.w(TAG, "Transfer stalled at " + transferred + "/" + size);
                    break;
                }
                
                transferred += actualTransferred;
            }
            
            return transferred == size;
            
        } catch (IOException e) {
            AppLog.e(TAG, "Error copying file: " + source.getName(), e);
            
            // 删除可能不完整 目标Файл
            if (target.exists()) {
                target.delete();
            }
            
            return false;
        } finally {
            try {
                if (sourceChannel != null) sourceChannel.close();
                if (targetChannel != null) targetChannel.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    /**
     * 格式化Файл大小
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
