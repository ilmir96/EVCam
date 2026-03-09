package com.kooo.evcam;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * автоматически适配宽Высокий比  TextureView
 * 根据Настройки 宽Высокий比автоматически调整视图尺寸，避免画面拉伸
 */
public class AutoFitTextureView extends TextureView {

    private int ratioWidth = 0;
    private int ratioHeight = 0;
    private boolean fillContainer = false;  //  否填满容器（而不 适应容器)

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Настройки此视图 宽Высокий比
     *
     * @param width  相 宽度
     * @param height 相 Высокий度
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        requestLayout();
    }

    /**
     * Настройки 否填满容器
     *
     * @param fill true=填满容器（可能裁切)，false=适应容器（可能有黑边)
     */
    public void setFillContainer(boolean fill) {
        this.fillContainer = fill;
        requestLayout();
    }


    /**
     * 根据Поворот 角度Настройки宽Высокий比
     * 当Поворот 角度为90°или270°时，会автоматически交换宽Высокий
     *
     * @param width    原始宽度
     * @param height   原始Высокий度
     * @param rotation Поворот 角度（0, 90, 180, 270)
     */
    public void setAspectRatioWithRotation(int width, int height, int rotation) {
        if (rotation == 90 || rotation == 270) {
            // Поворот 90°или270°时，宽Высокий互换
            setAspectRatio(height, width);
        } else {
            setAspectRatio(width, height);
        }
    }

    /**
     * ПолучениеТекущийНастройки 宽Высокий比
     * @return 宽Высокий比（宽/Высокий)，Если Не Настройки返回0
     */
    public float getAspectRatio() {
        if (ratioWidth == 0 || ratioHeight == 0) {
            return 0f;
        }
        return (float) ratioWidth / ratioHeight;
    }

    /**
     * ПолучениеТекущийНастройки 比例宽度
     */
    public int getRatioWidth() {
        return ratioWidth;
    }

    /**
     * ПолучениеТекущийНастройки 比例Высокий度
     */
    public int getRatioHeight() {
        return ratioHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height);
        } else {
            // 根据宽Высокий比调整尺寸
            int newWidth, newHeight;

            // 方案1：基于容器宽度计算Высокий度
            newWidth = width;
            newHeight = width * ratioHeight / ratioWidth;

            if (fillContainer) {
                // 填满режим：Если Высокий度不足，放大以填满容器
                if (newHeight < height) {
                    newHeight = height;
                    newWidth = height * ratioWidth / ratioHeight;
                }
            } else {
                // 适应режим：Если Высокий度超出，缩小以适应容器
                if (newHeight > height) {
                    newHeight = height;
                    newWidth = height * ratioWidth / ratioHeight;
                }
            }

            setMeasuredDimension(newWidth, newHeight);
        }
    }
}
