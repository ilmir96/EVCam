package com.kooo.evcam.heartbeat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Size;
import android.view.TextureView;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.camera.SingleCamera;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * МониторингИзображение处理器
 * 负责 от КамераПолучениеИзображение、拼接 и 压缩
 */
public class HeartbeatImageProcessor {
    private static final String TAG = "HeartbeatImageProcessor";
    
    /**
     *  от 多 шт.相机Получение实时画面并拼接
     * 
     * @param cameras SingleCamera 列表
     * @return 拼接后  Bitmap（调用方负责回收)，ОшибкаВозвращает null
     */
    public Bitmap captureAndMerge(List<SingleCamera> cameras) {
        if (cameras == null || cameras.isEmpty()) {
            AppLog.w(TAG, "相机列表пусто");
            return null;
        }
        
        List<Bitmap> bitmaps = new ArrayList<>();
        
        for (SingleCamera camera : cameras) {
            if (camera == null) {
                continue;
            }
            
            try {
                Bitmap bitmap = captureSingleCamera(camera);
                if (bitmap != null) {
                    bitmaps.add(bitmap);
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Получение相机画面Ошибка: " + e.getMessage());
            }
        }
        
        if (bitmaps.isEmpty()) {
            AppLog.w(TAG, "Не 能Получение任何相机画面");
            return null;
        }
        
        AppLog.d(TAG, "УспешноПолучение " + bitmaps.size() + "  шт.相机画面");
        
        // 拼接Изображение
        Bitmap merged = mergeBitmaps(bitmaps);
        
        // 回收原始 bitmap（拼接后不再необходимо)
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        
        return merged;
    }
    
    /**
     *  от 单 шт.相机Получение画面
     */
    private Bitmap captureSingleCamera(SingleCamera camera) {
        // Получение TextureView（通过反射или公Вкл方法)
        // 由于 SingleCamera 类 设计，我们необходимо 主线程Получение Bitmap
        // 这里假设 camera 有提供Получение Bitmap  方法
        
        Size previewSize = camera.getPreviewSize();
        if (previewSize == null) {
            AppLog.w(TAG, "相机 " + camera.getCameraId() + " 预览尺寸Неизвестно");
            return null;
        }
        
        // использование SingleCamera Внутреннее 方法Получение Bitmap
        // 注意：这необходимо  SingleCamera 添加一 шт.公Вкл方法
        return camera.captureBitmap();
    }
    
    /**
     * 拼接 Bitmap
     * - 1 шт.：原图
     * - 2 шт.：横 к 拼接 (W*2, H)
     * - 3-4 шт.：四宫格 (W*2, H*2)
     * 
     * @param bitmaps Bitmap 列表
     * @return 拼接后  Bitmap
     */
    private Bitmap mergeBitmaps(List<Bitmap> bitmaps) {
        int count = bitmaps.size();
        Bitmap first = bitmaps.get(0);
        int w = first.getWidth();
        int h = first.getHeight();
        
        AppLog.d(TAG, "拼接 " + count + "  шт.Изображение，单 шт.尺寸: " + w + "x" + h);
        
        if (count == 1) {
            // 单 шт.Изображение，直接复制返回
            return first.copy(Bitmap.Config.ARGB_8888, false);
        }
        
        if (count == 2) {
            // 横 к 拼接
            Bitmap result = Bitmap.createBitmap(w * 2, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.drawBitmap(bitmaps.get(0), 0, 0, null);
            canvas.drawBitmap(bitmaps.get(1), w, 0, null);
            AppLog.d(TAG, "横 к 拼接завершение，尺寸: " + (w * 2) + "x" + h);
            return result;
        }
        
        // 四宫格 (3или4 шт.)
        Bitmap result = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.BLACK); // 背景色（3时右角填黑)
        
        // 左
        canvas.drawBitmap(bitmaps.get(0), 0, 0, null);
        // 右
        canvas.drawBitmap(bitmaps.get(1), w, 0, null);
        // 左
        canvas.drawBitmap(bitmaps.get(2), 0, h, null);
        // 右（Если 有第4 шт.)
        if (count >= 4) {
            canvas.drawBitmap(bitmaps.get(3), w, h, null);
        }
        
        AppLog.d(TAG, "四宫格拼接завершение，尺寸: " + (w * 2) + "x" + (h * 2));
        return result;
    }
    
    /**
     * 压缩 Bitmap  до 目标大小
     * использование二分法动态调整 JPEG 质量
     * 
     * @param bitmap 原图
     * @param targetSizeKB 目标大小（KB)，0 表示不压缩
     * @return 压缩后  byte[]
     */
    public byte[] compressToTargetSize(Bitmap bitmap, int targetSizeKB) {
        if (bitmap == null) {
            return null;
        }
        
        // 不压缩：использование 95% 质量
        if (targetSizeKB <= 0) {
            AppLog.d(TAG, "不压缩режим，использование 95% 质量");
            return compressWithQuality(bitmap, 95);
        }
        
        int targetSizeBytes = targetSizeKB * 1024;
        int minQualityLimit = 10;  // 最Низкий质量限制
        int minQuality = minQualityLimit;
        int maxQuality = 95;
        int quality = 70; // 初始质量
        byte[] result = null;
        int iterations = 0;
        int maxIterations = 6;  // 最多6 раз迭代，足够覆盖10-95范围
        
        // 二分法查找最佳质量
        while (minQuality <= maxQuality && iterations < maxIterations) {
            iterations++;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            result = baos.toByteArray();
            
            int currentSize = result.length;
            int currentSizeKB = currentSize / 1024;
            
            // 容差：目标  20% или 20KB，取较大值，让它更早收敛
            int tolerance = Math.max(20, targetSizeKB / 5);
            if (Math.abs(currentSizeKB - targetSizeKB) <= tolerance) {
                AppLog.d(TAG, "压缩завершение: 质量=" + quality + ", 大小=" + currentSizeKB + "KB (目标=" + targetSizeKB + "KB), 迭代=" + iterations);
                break;
            }
            
            // Если 经 до 最Низкий质量，不再降
            if (quality <= minQualityLimit) {
                AppLog.d(TAG, "达最Низкий质量 " + minQualityLimit + "%, 大小=" + currentSizeKB + "KB (目标=" + targetSizeKB + "KB)");
                break;
            }
            
            if (currentSize > targetSizeBytes) {
                maxQuality = quality - 1;
            } else {
                minQuality = quality + 1;
            }
            quality = Math.max(minQualityLimit, (minQuality + maxQuality) / 2);
        }
        
        if (result != null) {
            AppLog.d(TAG, "最终压缩结果: " + (result.length / 1024) + "KB, 迭代 раз数: " + iterations);
        }
        
        return result;
    }
    
    /**
     * 压缩 Bitmap  до 指定质量
     * 
     * @param bitmap 原图
     * @param quality JPEG 质量 (0-100)
     * @return 压缩后  byte[]
     */
    public byte[] compressWithQuality(Bitmap bitmap, int quality) {
        if (bitmap == null) {
            return null;
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return baos.toByteArray();
    }
}
