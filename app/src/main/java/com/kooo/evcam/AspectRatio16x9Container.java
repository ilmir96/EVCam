package com.kooo.evcam;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * 自定义FrameLayout，保持16:9 宽Высокий比
 * 用于竖屏режим 预览区容器，确保预览区不 压缩
 * 根据宽度计算Высокий度，保证16:9比例
 */
public class AspectRatio16x9Container extends FrameLayout {
    // 宽Высокий比 16:9
    private static final float WIDTH_RATIO = 16.0f;
    private static final float HEIGHT_RATIO = 9.0f;

    public AspectRatio16x9Container(Context context) {
        super(context);
    }

    public AspectRatio16x9Container(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatio16x9Container(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int finalWidth, finalHeight;

        // 竖屏режим，通常宽度 确定 ，根据宽度计算16:9 Высокий度
        if (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST) {
            // 基于宽度计算Высокий度（16:9)
            finalWidth = widthSize;
            finalHeight = (int) (widthSize * HEIGHT_RATIO / WIDTH_RATIO);
            
            // Если Высокий度有限制，确保不超出
            if (heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST) {
                if (finalHeight > heightSize) {
                    // Высокий度超出限制，基于Высокий度反算宽度
                    finalHeight = heightSize;
                    finalWidth = (int) (heightSize * WIDTH_RATIO / HEIGHT_RATIO);
                }
            }
        } else if (heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST) {
            // 宽度不确定但Высокий度确定，基于Высокий度计算宽度
            finalHeight = heightSize;
            finalWidth = (int) (heightSize * WIDTH_RATIO / HEIGHT_RATIO);
        } else {
            // все不确定，использованиеПо умолчанию
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int newWidthSpec = MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY);
        int newHeightSpec = MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY);
        super.onMeasure(newWidthSpec, newHeightSpec);
    }
}
