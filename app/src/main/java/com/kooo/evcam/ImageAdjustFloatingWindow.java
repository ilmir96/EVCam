package com.kooo.evcam;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Range;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.kooo.evcam.camera.ImageAdjustManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 亮度/Шумоподавление调节悬浮窗
 * 提供实时调节相机参数 UI
 */
public class ImageAdjustFloatingWindow {
    private static final String TAG = "ImageAdjustFloating";
    
    private final Context context;
    private final WindowManager windowManager;
    private final ImageAdjustManager adjustManager;
    private final AppConfig appConfig;
    
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;
    private boolean isShowing = false;
    
    // UI 控件
    private SeekBar exposureSeekBar;
    private TextView exposureValueText;
    private TextView awbSelectText;
    private TextView tonemapSelectText;
    private TextView edgeSelectText;
    private TextView noiseReductionSelectText;
    private TextView effectSelectText;
    
    // 数据映射（值列表)
    private List<Integer> awbModeValues = new ArrayList<>();
    private List<Integer> tonemapModeValues = new ArrayList<>();
    private List<Integer> edgeModeValues = new ArrayList<>();
    private List<Integer> noiseReductionModeValues = new ArrayList<>();
    private List<Integer> effectModeValues = new ArrayList<>();
    
    // 数据映射（选项名称列表)
    private List<String> awbOptions = new ArrayList<>();
    private List<String> tonemapOptions = new ArrayList<>();
    private List<String> edgeOptions = new ArrayList<>();
    private List<String> noiseReductionOptions = new ArrayList<>();
    private List<String> effectOptions = new ArrayList<>();
    
    // 回调接口
    public interface OnDismissListener {
        void onDismiss();
    }
    
    private OnDismissListener dismissListener;
    
    public ImageAdjustFloatingWindow(Context context, ImageAdjustManager adjustManager) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.adjustManager = adjustManager;
        this.appConfig = new AppConfig(context);
    }
    
    public void setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
    }
    
    /**
     * 显示悬浮窗
     */
    public void show() {
        if (isShowing) {
            return;
        }
        
        // 创建悬浮窗视图
        floatingView = createFloatingView();
        
        // Настройки窗口参数
        int windowType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            windowType = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        // FLAG_NOT_TOUCH_MODAL: разрешить悬浮窗外 触摸事件传递 до 层窗口
        // 不использование FLAG_NOT_FOCUSABLE，这样 Spinner 才能нормально响应点击
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        
        // Настройки初始Позиция（屏幕右侧间)
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 50;
        layoutParams.y = 100;
        
        try {
            windowManager.addView(floatingView, layoutParams);
            isShowing = true;
            AppLog.d(TAG, "Floating window shown");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to show floating window", e);
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    public void dismiss() {
        if (!isShowing || floatingView == null) {
            return;
        }
        
        try {
            windowManager.removeView(floatingView);
            floatingView = null;
            isShowing = false;
            
            // Сохранить参数 до конфигурация
            adjustManager.saveParamsToConfig();
            
            if (dismissListener != null) {
                dismissListener.onDismiss();
            }
            
            AppLog.d(TAG, "Floating window dismissed");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to dismiss floating window", e);
        }
    }
    
    /**
     * проверка悬浮窗 否Выполняется 显示
     */
    public boolean isShowing() {
        return isShowing;
    }
    
    /**
     * 创建悬浮窗视图
     */
    private View createFloatingView() {
        // 主容器 - Настройки固定宽度使悬浮窗更窄
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp2px(12), dp2px(10), dp2px(12), dp2px(10));
        mainLayout.setLayoutParams(new ViewGroup.LayoutParams(dp2px(220), ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // Настройки背景（圆角半透明)
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(235, 35, 35, 35));
        background.setCornerRadius(dp2px(10));
        mainLayout.setBackground(background);
        
        // 标题栏（可拖动)
        LinearLayout titleBar = createTitleBar();
        mainLayout.addView(titleBar);
        
        // 滚动区域 - 增加Высокий度以完整显示内容
        ScrollView scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(380)  // 增加Высокий度以显示所有内容
        ));
        
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(0, dp2px(4), 0, dp2px(4));
        
        // Экспозиция
        if (adjustManager.isExposureCompensationSupported()) {
            contentLayout.addView(createExposureSection());
        }
        
        // Баланс белого
        if (adjustManager.isAwbModeSupported()) {
            contentLayout.addView(createAwbSection());
        }
        
        // Тональная компрессия
        if (adjustManager.isTonemapModeSupported()) {
            contentLayout.addView(createTonemapSection());
        }
        
        // Резкость
        if (adjustManager.isEdgeModeSupported()) {
            contentLayout.addView(createEdgeSection());
        }
        
        // Шумоподавление
        if (adjustManager.isNoiseReductionModeSupported()) {
            contentLayout.addView(createNoiseReductionSection());
        }
        
        // Эффекты
        if (adjustManager.isEffectModeSupported()) {
            contentLayout.addView(createEffectSection());
        }
        
        // Если 没有可调节 参数
        if (contentLayout.getChildCount() == 0) {
            TextView noParamsText = new TextView(context);
            noParamsText.setText("Текущее устройство не поддерживает настройку яркости/шумоподавления");
            noParamsText.setTextColor(Color.GRAY);
            noParamsText.setTextSize(14);
            noParamsText.setPadding(0, dp2px(20), 0, dp2px(20));
            contentLayout.addView(noParamsText);
        }
        
        scrollView.addView(contentLayout);
        mainLayout.addView(scrollView);
        
        // 底部按钮
        LinearLayout bottomBar = createBottomBar();
        mainLayout.addView(bottomBar);
        
        return mainLayout;
    }
    
    /**
     * 创建标题栏
     */
    private LinearLayout createTitleBar() {
        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(0, 0, 0, dp2px(4));
        
        // 标题
        TextView titleText = new TextView(context);
        titleText.setText("Яркость/шумоподавление");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(16);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        titleBar.addView(titleText);
        
        // Закрыто按钮
        Button closeButton = new Button(context);
        closeButton.setText("×");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(18);
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        closeButton.setPadding(dp2px(8), 0, dp2px(8), 0);
        closeButton.setOnClickListener(v -> dismiss());
        titleBar.addView(closeButton);
        
        // Настройки拖动функция
        titleBar.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, layoutParams);
                        return true;
                }
                return false;
            }
        });
        
        return titleBar;
    }
    
    /**
     * 创建Экспозиция区域
     */
    private LinearLayout createExposureSection() {
        LinearLayout section = createSection("Экспозиция");
        
        Range<Integer> range = adjustManager.getExposureRange();
        if (range == null) {
            return section;
        }
        
        // 显示Текущий值
        exposureValueText = new TextView(context);
        exposureValueText.setTextColor(Color.LTGRAY);
        exposureValueText.setTextSize(12);
        updateExposureValueText();
        section.addView(exposureValueText);
        
        // SeekBar
        exposureSeekBar = new SeekBar(context);
        exposureSeekBar.setMax(range.getUpper() - range.getLower());
        exposureSeekBar.setProgress(adjustManager.getExposureCompensation() - range.getLower());
        exposureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int value = progress + range.getLower();
                    adjustManager.setExposureCompensation(value);
                    updateExposureValueText();
                    adjustManager.updateAllCameras();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        section.addView(exposureSeekBar);
        
        // 范围Уведомление
        LinearLayout rangeHint = new LinearLayout(context);
        rangeHint.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView minText = new TextView(context);
        minText.setText(String.valueOf(range.getLower()));
        minText.setTextColor(Color.GRAY);
        minText.setTextSize(10);
        minText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rangeHint.addView(minText);
        
        TextView maxText = new TextView(context);
        maxText.setText(String.valueOf(range.getUpper()));
        maxText.setTextColor(Color.GRAY);
        maxText.setTextSize(10);
        maxText.setGravity(Gravity.END);
        maxText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rangeHint.addView(maxText);
        
        section.addView(rangeHint);
        
        return section;
    }
    
    /**
     * обновление曝光值显示
     */
    private void updateExposureValueText() {
        if (exposureValueText != null) {
            int value = adjustManager.getExposureCompensation();
            String text = (value >= 0 ? "+" : "") + value;
            exposureValueText.setText("Текущее: " + text + " (темнее ← → ярче)");
        }
    }
    
    /**
     * 创建Баланс белого区域
     */
    private LinearLayout createAwbSection() {
        LinearLayout section = createSection("Баланс белого");
        
        awbOptions.clear();
        awbModeValues.clear();
        
        // 添加По умолчанию选项
        awbOptions.add("По умолчанию");
        awbModeValues.add(AppConfig.AWB_MODE_DEFAULT);
        
        int[] supportedModes = adjustManager.getSupportedAwbModes();
        if (supportedModes != null) {
            for (int mode : supportedModes) {
                awbOptions.add(AppConfig.getAwbModeDisplayName(mode));
                awbModeValues.add(mode);
            }
        }
        
        awbSelectText = createSelectTextView(
                getDisplayName(awbOptions, awbModeValues, adjustManager.getAwbMode()),
                v -> showSelectionDialog("Баланс белого", awbOptions, awbModeValues, adjustManager.getAwbMode(), 
                        value -> {
                            adjustManager.setAwbMode(value);
                            adjustManager.updateAllCameras();
                            awbSelectText.setText(getDisplayName(awbOptions, awbModeValues, value) + " ▼");
                        })
        );
        section.addView(awbSelectText);
        
        return section;
    }
    
    /**
     * 创建Тональная компрессия区域
     */
    private LinearLayout createTonemapSection() {
        LinearLayout section = createSection("Тонирование");
        
        tonemapOptions.clear();
        tonemapModeValues.clear();
        
        tonemapOptions.add("По умолчанию");
        tonemapModeValues.add(AppConfig.TONEMAP_MODE_DEFAULT);
        
        int[] supportedModes = adjustManager.getSupportedTonemapModes();
        if (supportedModes != null) {
            for (int mode : supportedModes) {
                tonemapOptions.add(AppConfig.getTonemapModeDisplayName(mode));
                tonemapModeValues.add(mode);
            }
        }
        
        tonemapSelectText = createSelectTextView(
                getDisplayName(tonemapOptions, tonemapModeValues, adjustManager.getTonemapMode()),
                v -> showSelectionDialog("Тонирование", tonemapOptions, tonemapModeValues, adjustManager.getTonemapMode(),
                        value -> {
                            adjustManager.setTonemapMode(value);
                            adjustManager.updateAllCameras();
                            tonemapSelectText.setText(getDisplayName(tonemapOptions, tonemapModeValues, value) + " ▼");
                        })
        );
        section.addView(tonemapSelectText);
        
        return section;
    }
    
    /**
     * 创建Резкость区域
     */
    private LinearLayout createEdgeSection() {
        LinearLayout section = createSection("Резкость");
        
        edgeOptions.clear();
        edgeModeValues.clear();
        
        edgeOptions.add("По умолчанию");
        edgeModeValues.add(AppConfig.EDGE_MODE_DEFAULT);
        
        int[] supportedModes = adjustManager.getSupportedEdgeModes();
        if (supportedModes != null) {
            for (int mode : supportedModes) {
                edgeOptions.add(AppConfig.getEdgeModeDisplayName(mode));
                edgeModeValues.add(mode);
            }
        }
        
        edgeSelectText = createSelectTextView(
                getDisplayName(edgeOptions, edgeModeValues, adjustManager.getEdgeMode()),
                v -> showSelectionDialog("Резкость", edgeOptions, edgeModeValues, adjustManager.getEdgeMode(),
                        value -> {
                            adjustManager.setEdgeMode(value);
                            adjustManager.updateAllCameras();
                            edgeSelectText.setText(getDisplayName(edgeOptions, edgeModeValues, value) + " ▼");
                        })
        );
        section.addView(edgeSelectText);
        
        return section;
    }
    
    /**
     * 创建Шумоподавление区域
     */
    private LinearLayout createNoiseReductionSection() {
        LinearLayout section = createSection("Шумоподавление");
        
        noiseReductionOptions.clear();
        noiseReductionModeValues.clear();
        
        noiseReductionOptions.add("По умолчанию");
        noiseReductionModeValues.add(AppConfig.NOISE_REDUCTION_DEFAULT);
        
        int[] supportedModes = adjustManager.getSupportedNoiseReductionModes();
        if (supportedModes != null) {
            for (int mode : supportedModes) {
                noiseReductionOptions.add(AppConfig.getNoiseReductionModeDisplayName(mode));
                noiseReductionModeValues.add(mode);
            }
        }
        
        noiseReductionSelectText = createSelectTextView(
                getDisplayName(noiseReductionOptions, noiseReductionModeValues, adjustManager.getNoiseReductionMode()),
                v -> showSelectionDialog("Шумоподавление", noiseReductionOptions, noiseReductionModeValues, adjustManager.getNoiseReductionMode(),
                        value -> {
                            adjustManager.setNoiseReductionMode(value);
                            adjustManager.updateAllCameras();
                            noiseReductionSelectText.setText(getDisplayName(noiseReductionOptions, noiseReductionModeValues, value) + " ▼");
                        })
        );
        section.addView(noiseReductionSelectText);
        
        return section;
    }
    
    /**
     * 创建Эффекты区域
     */
    private LinearLayout createEffectSection() {
        LinearLayout section = createSection("Эффекты");
        
        effectOptions.clear();
        effectModeValues.clear();
        
        effectOptions.add("По умолчанию");
        effectModeValues.add(AppConfig.EFFECT_MODE_DEFAULT);
        
        int[] supportedModes = adjustManager.getSupportedEffectModes();
        if (supportedModes != null) {
            for (int mode : supportedModes) {
                effectOptions.add(AppConfig.getEffectModeDisplayName(mode));
                effectModeValues.add(mode);
            }
        }
        
        effectSelectText = createSelectTextView(
                getDisplayName(effectOptions, effectModeValues, adjustManager.getEffectMode()),
                v -> showSelectionDialog("Эффекты", effectOptions, effectModeValues, adjustManager.getEffectMode(),
                        value -> {
                            adjustManager.setEffectMode(value);
                            adjustManager.updateAllCameras();
                            effectSelectText.setText(getDisplayName(effectOptions, effectModeValues, value) + " ▼");
                        })
        );
        section.addView(effectSelectText);
        
        return section;
    }
    
    /**
     * 创建区域容器
     */
    private LinearLayout createSection(String title) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp2px(6), 0, dp2px(6));
        
        // 分隔线
        View divider = new View(context);
        divider.setBackgroundColor(Color.argb(50, 255, 255, 255));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        ));
        section.addView(divider);
        
        // 标题
        TextView titleText = new TextView(context);
        titleText.setText(title);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(14);
        titleText.setPadding(0, dp2px(8), 0, dp2px(4));
        section.addView(titleText);
        
        return section;
    }
    
    /**
     * 创建可点击 Выбрать文本视图
     */
    private TextView createSelectTextView(String text, View.OnClickListener clickListener) {
        TextView textView = new TextView(context);
        textView.setText(text + " ▼");
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(14);
        textView.setPadding(dp2px(12), dp2px(10), dp2px(12), dp2px(10));
        textView.setBackgroundColor(Color.argb(60, 255, 255, 255));
        textView.setOnClickListener(clickListener);
        
        // Настройки圆角背景
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(40, 255, 255, 255));
        bg.setCornerRadius(dp2px(4));
        textView.setBackground(bg);
        
        return textView;
    }
    
    /**
     * 显示Выбрать 话框（会显示 悬浮窗方)
     */
    private void showSelectionDialog(String title, List<String> options, List<Integer> values,
                                     int currentValue, OnValueSelectedListener listener) {
        // 找 до Текущий选项
        int checkedItem = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == currentValue) {
                checkedItem = i;
                break;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setSingleChoiceItems(options.toArray(new String[0]), checkedItem, (dialog, which) -> {
            if (which < values.size()) {
                listener.onSelected(values.get(which));
            }
            dialog.dismiss();
        });
        builder.setNegativeButton("Отмена", null);
        
        AlertDialog dialog = builder.create();
        
        // Настройки 话框窗口类型，使其显示 悬浮窗方
        Window window = dialog.getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                window.setType(WindowManager.LayoutParams.TYPE_PHONE);
            }
        }
        
        dialog.show();
    }
    
    /**
     * 根据值Получение显示名称
     */
    private String getDisplayName(List<String> options, List<Integer> values, int value) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == value) {
                return options.get(i);
            }
        }
        return options.isEmpty() ? "Неизвестно" : options.get(0);
    }
    
    /**
     * 值Выбрать监听器
     */
    private interface OnValueSelectedListener {
        void onSelected(int value);
    }
    
    /**
     * 创建底部按钮栏
     */
    private LinearLayout createBottomBar() {
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(0, dp2px(8), 0, 0);
        
        // Сброс按钮
        Button resetButton = new Button(context);
        resetButton.setText("По умолчанию");
        resetButton.setTextSize(12);
        resetButton.setOnClickListener(v -> {
            adjustManager.resetToDefault();
            refreshUI();  // Обновить UI 控件以反映Сброс后 值
        });
        bottomBar.addView(resetButton);
        
        // 间距
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp2px(16), 0));
        bottomBar.addView(spacer);
        
        // Сохранить并Закрыто按钮
        Button saveButton = new Button(context);
        saveButton.setText("Сохранить и закрыть");
        saveButton.setTextSize(12);
        saveButton.setOnClickListener(v -> dismiss());
        bottomBar.addView(saveButton);
        
        return bottomBar;
    }
    
    /**
     * Обновить UI 控件以反映Текущий参数值
     */
    private void refreshUI() {
        // ОбновитьЭкспозиция
        if (exposureSeekBar != null && adjustManager.isExposureCompensationSupported()) {
            Range<Integer> range = adjustManager.getExposureRange();
            if (range != null) {
                exposureSeekBar.setProgress(adjustManager.getExposureCompensation() - range.getLower());
                updateExposureValueText();
            }
        }
        
        // ОбновитьБаланс белого
        if (awbSelectText != null && !awbOptions.isEmpty()) {
            awbSelectText.setText(getDisplayName(awbOptions, awbModeValues, adjustManager.getAwbMode()) + " ▼");
        }
        
        // ОбновитьТональная компрессия
        if (tonemapSelectText != null && !tonemapOptions.isEmpty()) {
            tonemapSelectText.setText(getDisplayName(tonemapOptions, tonemapModeValues, adjustManager.getTonemapMode()) + " ▼");
        }
        
        // ОбновитьРезкость
        if (edgeSelectText != null && !edgeOptions.isEmpty()) {
            edgeSelectText.setText(getDisplayName(edgeOptions, edgeModeValues, adjustManager.getEdgeMode()) + " ▼");
        }
        
        // ОбновитьШумоподавление
        if (noiseReductionSelectText != null && !noiseReductionOptions.isEmpty()) {
            noiseReductionSelectText.setText(getDisplayName(noiseReductionOptions, noiseReductionModeValues, adjustManager.getNoiseReductionMode()) + " ▼");
        }
        
        // ОбновитьЭффекты
        if (effectSelectText != null && !effectOptions.isEmpty()) {
            effectSelectText.setText(getDisplayName(effectOptions, effectModeValues, adjustManager.getEffectMode()) + " ▼");
        }
        
        AppLog.d(TAG, "UI refreshed after reset");
    }
    
    /**
     * dp 转 px
     */
    private int dp2px(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
