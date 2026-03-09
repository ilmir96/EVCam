package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Видео缩略图提取инструмент
 * 用于 от ВидеоФайл提取Первый帧作为封面图
 */
public class VideoThumbnailExtractor {
    private static final String TAG = "VideoThumbnailExtractor";

    /**
     *  от ВидеоФайл提取封面图
     * @param videoFile ВидеоФайл
     * @param outputFile 输出 封面图Файл
     * @return  否Успешно
     */
    public static boolean extractThumbnail(File videoFile, File outputFile) {
        MediaMetadataRetriever retriever = null;
        FileOutputStream fos = null;

        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoFile.getAbsolutePath());

            // ПолучениеПервый帧（时间为 0 微 сек.)
            Bitmap bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

            if (bitmap == null) {
                AppLog.e(TAG, "无法 от Видео提取帧: " + videoFile.getName());
                return false;
            }

            // Сохранить为 JPEG Файл
            fos = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();

            AppLog.d(TAG, "封面图提取Успешно: " + outputFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            AppLog.e(TAG, "提取封面图Ошибка: " + videoFile.getName(), e);
            return false;
        } finally {
            try {
                if (retriever != null) {
                    retriever.release();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                AppLog.e(TAG, "Закрыто资源Ошибка", e);
            }
        }
    }

    /**
     * ПолучениеВидео时长（ сек.)
     * @param videoFile ВидеоФайл
     * @return Видео时长，Ошибка返回 0
     */
    public static int getVideoDuration(File videoFile) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoFile.getAbsolutePath());

            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                long durationMs = Long.parseLong(durationStr);
                int durationSec = (int) (durationMs / 1000);
                AppLog.d(TAG, "Видео时长: " + durationSec + "  сек.");
                return durationSec;
            }
        } catch (Exception e) {
            AppLog.e(TAG, "ПолучениеВидео时长Ошибка: " + videoFile.getName(), e);
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    AppLog.e(TAG, "释放 MediaMetadataRetriever Ошибка", e);
                }
            }
        }
        return 0;
    }
}
