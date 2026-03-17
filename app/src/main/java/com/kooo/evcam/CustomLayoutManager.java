package com.kooo.evcam;

import android.content.Context;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Своя модель布局управление器
 * управлениеКамера窗口 и 按钮区域 自由操控функция：拖动、缩放、隐藏、Поворот 、镜像
 */
public class CustomLayoutManager {
    private static final String TAG = "CustomLayoutManager";

    // 尺寸调整比例
    private static final float SCALE_STEP = 0.05f;  // 每 раз5%
    private static final float MIN_SCALE = 0.2f;    // минимум20%
    private static final float MAX_SCALE = 3.0f;    // максимум300%
    
    // 网格吸附像素
    private static final int GRID_SIZE = 10;

    // Мульти-камерный вид边距（dp)
    private static final int SIDE_MARGIN_DP = 20;   // 行左Правая камера外侧边距
    private static final int GAP_DP = 20;            // Камера之间间距
    private static final int LAYOUT_VERSION = 3;

    private final Context context;
    private final AppConfig appConfig;
    
    // 编辑режимСтатус
    private boolean editModeEnabled = false;
    
    // управление 视图
    private FrameLayout frameFront;
    private FrameLayout frameBack;
    private FrameLayout frameLeft;
    private FrameLayout frameRight;
    private FrameLayout frameVehicleControl;
    private ViewGroup buttonContainer;
    
    // TextureView 引用（用于Поворот  и 镜像)
    private TextureView textureFront;
    private TextureView textureBack;
    private TextureView textureLeft;
    private TextureView textureRight;
    
    // 编辑控制视图
    private View editControlsView;
    
    // Камера总容器（用于计算拖动边界)
    private View containerCameras;
    
    // конфигурация Камера数量
    private int cameraCount = 4;
    
    // 按钮布局变更回调
    private OnButtonLayoutChangeListener buttonLayoutChangeListener;
    
    // 布局数据
    private LayoutData layoutData;
    
    /**
     * 按钮布局变更监听器
     */
    public interface OnButtonLayoutChangeListener {
        void onButtonLayoutChange(String orientation);
    }

    public CustomLayoutManager(Context context) {
        this.context = context;
        this.appConfig = new AppConfig(context);
        this.layoutData = new LayoutData();
        loadLayoutData();
    }

    /**
     * Настройки按钮布局变更监听器
     */
    public void setOnButtonLayoutChangeListener(OnButtonLayoutChangeListener listener) {
        this.buttonLayoutChangeListener = listener;
    }
    
    /**
     * НастройкиКамера数量
     */
    public void setCameraCount(int count) {
        this.cameraCount = count;
    }
    
    /**
     * обновление按钮容器引用（当按钮方 к 切换时调用)
     * @param newContainer 新 按钮容器
     */
    public void updateButtonContainer(ViewGroup newContainer) {
        this.buttonContainer = newContainer;
        if (newContainer != null) {
            setupButtonContainer(newContainer);
        }
        AppLog.d(TAG, "按钮容器обновление");
    }
    
    /**
     * инициализация自由操控функция
     */
    public void setupFloatingViews(FrameLayout frameFront, FrameLayout frameBack,
                                   FrameLayout frameLeft, FrameLayout frameRight, FrameLayout frameVehicleControl,
                                   ViewGroup buttonContainer, View editControlsView,
                                   View containerCameras,
                                   TextureView textureFront, TextureView textureBack,
                                   TextureView textureLeft, TextureView textureRight) {
        this.frameFront = frameFront;
        this.frameBack = frameBack;
        this.frameLeft = frameLeft;
        this.frameRight = frameRight;
        this.frameVehicleControl = frameVehicleControl;
        this.buttonContainer = buttonContainer;
        this.editControlsView = editControlsView;
        this.containerCameras = containerCameras;
        this.textureFront = textureFront;
        this.textureBack = textureBack;
        this.textureLeft = textureLeft;
        this.textureRight = textureRight;
        
        // ВосстановлениеСохранить 布局илиНастройкиПо умолчаниюПозиция
        if (!restoreLayout()) {
            // 没有Сохранить 布局，НастройкиПо умолчаниюПозиция
            containerCameras.post(this::setupDefaultPositions);
        }
        
        // Настройки拖动 и 缩放функция
        if (frameFront != null) {
            setupCameraFrame(frameFront, "front");
            setupRotateMirrorButtons(frameFront, "front", textureFront);
        }
        if (frameBack != null) {
            setupCameraFrame(frameBack, "back");
            setupRotateMirrorButtons(frameBack, "back", textureBack);
        }
        if (frameLeft != null) {
            setupCameraFrame(frameLeft, "left");
            setupRotateMirrorButtons(frameLeft, "left", textureLeft);
        }
        if (frameRight != null) {
            setupCameraFrame(frameRight, "right");
            setupRotateMirrorButtons(frameRight, "right", textureRight);
        }
        if (frameVehicleControl != null) {
            setupVehicleControlButtons();
        }
        if (buttonContainer != null) {
            setupButtonContainer(buttonContainer);
        }
        
        // Настройки编辑控制面板 按钮
        setupEditControlButtons();
        
        // инициализация编辑режим
        setEditMode(appConfig.isCustomFreeControlEnabled());
        
        // 延迟ПриложениеСохранить Поворот  и 镜像конфигурация（необходимоожидание视图布局завершение)
        if (containerCameras != null) {
            containerCameras.post(this::applySavedRotationAndMirror);
        }
    }
    
    /**
     * ПриложениеСохранить Поворот  и 镜像конфигурация
     *  视图布局завершение后调用，以确保 TextureView 有正确 尺寸
     */
    private void applySavedRotationAndMirror() {
        if (textureFront != null) {
            int rotation = appConfig.getCameraRotation("front");
            boolean mirror = appConfig.getCameraMirror("front");
            if (rotation != 0) {
                applyRotationWithScale(textureFront, rotation);
            }
            if (mirror) {
                applyMirrorWithRotation(textureFront, "front", mirror);
            }
        }
        if (textureBack != null) {
            int rotation = appConfig.getCameraRotation("back");
            boolean mirror = appConfig.getCameraMirror("back");
            if (rotation != 0) {
                applyRotationWithScale(textureBack, rotation);
            }
            if (mirror) {
                applyMirrorWithRotation(textureBack, "back", mirror);
            }
        }
        if (textureLeft != null) {
            int rotation = appConfig.getCameraRotation("left");
            boolean mirror = appConfig.getCameraMirror("left");
            if (rotation != 0) {
                applyRotationWithScale(textureLeft, rotation);
            }
            if (mirror) {
                applyMirrorWithRotation(textureLeft, "left", mirror);
            }
        }
        if (textureRight != null) {
            int rotation = appConfig.getCameraRotation("right");
            boolean mirror = appConfig.getCameraMirror("right");
            if (rotation != 0) {
                applyRotationWithScale(textureRight, rotation);
            }
            if (mirror) {
                applyMirrorWithRotation(textureRight, "right", mirror);
            }
        }
        
        // ПриложениеСохранить 裁剪конфигурация
        applySavedCrops();
    }
    
    /**
     * ПриложениеПоворот 并调整缩放，使画面填满容器
     * 当Поворот 90°или270°时，необходимо缩放画面以填满原来 容器
     */
    private void applyRotationWithScale(TextureView textureView, int rotation) {
        textureView.setRotation(rotation);

        if (rotation == 90 || rotation == 270) {
            // 优先использование LayoutParams  目标尺寸（setLayoutParams 后布局尚Не Обновить时
            // getWidth/Height 仍返回旧值，导致缩放比例算错)
            android.view.ViewGroup.LayoutParams lp = textureView.getLayoutParams();
            int width = (lp != null && lp.width > 0) ? lp.width : textureView.getWidth();
            int height = (lp != null && lp.height > 0) ? lp.height : textureView.getHeight();

            if (width > 0 && height > 0) {
                float scale = Math.max((float) width / height, (float) height / width);
                textureView.setScaleY(scale);
                float currentScaleX = textureView.getScaleX();
                textureView.setScaleX(currentScaleX < 0 ? -scale : scale);
                AppLog.d(TAG, "Поворот  " + rotation + "° 缩放: " + scale + " (w=" + width + " h=" + height + ")");
            }
        } else {
            float currentScaleX = textureView.getScaleX();
            textureView.setScaleX(currentScaleX < 0 ? -1.0f : 1.0f);
            textureView.setScaleY(1.0f);
        }
    }
    
    /**
     * Приложение镜像，同时考虑Текущий Поворот Статус
     */
    private void applyMirrorWithRotation(TextureView textureView, String cameraKey, boolean mirror) {
        int rotation = appConfig.getCameraRotation(cameraKey);
        
        float baseScale = 1.0f;
        // Если Текущий 90°или270°Поворот ，необходимо保持缩放
        if (rotation == 90 || rotation == 270) {
            int width = textureView.getWidth();
            int height = textureView.getHeight();
            if (width > 0 && height > 0) {
                baseScale = Math.max((float) width / height, (float) height / width);
            }
        }
        
        // Приложение镜像（负值表示镜像)
        textureView.setScaleX(mirror ? -baseScale : baseScale);
    }
    
    /**
     * НастройкиПоворот  и 镜像按钮
     */
    private void setupRotateMirrorButtons(FrameLayout frame, String cameraKey, TextureView textureView) {
        // Поворот 按钮
        int rotateBtnId = context.getResources().getIdentifier(
                "btn_rotate_" + cameraKey, "id", context.getPackageName());
        View rotateBtn = frame.findViewById(rotateBtnId);
        if (rotateBtn != null) {
            rotateBtn.setOnClickListener(v -> {
                int currentRotation = appConfig.getCameraRotation(cameraKey);
                int newRotation = (currentRotation + 90) % 360;
                appConfig.setCameraRotation(cameraKey, newRotation);
                
                if (textureView != null) {
                    applyRotationWithScale(textureView, newRotation);
                }
                
                Toast.makeText(context, cameraKey + " Поворот: " + newRotation + "°", 
                        Toast.LENGTH_SHORT).show();
                AppLog.d(TAG, cameraKey + " Поворот Настройки为: " + newRotation + "°");
            });
        }
        
        // 镜像按钮
        int mirrorBtnId = context.getResources().getIdentifier(
                "btn_mirror_" + cameraKey, "id", context.getPackageName());
        View mirrorBtn = frame.findViewById(mirrorBtnId);
        if (mirrorBtn != null) {
            mirrorBtn.setOnClickListener(v -> {
                boolean currentMirror = appConfig.getCameraMirror(cameraKey);
                boolean newMirror = !currentMirror;
                appConfig.setCameraMirror(cameraKey, newMirror);
                
                if (textureView != null) {
                    applyMirrorWithRotation(textureView, cameraKey, newMirror);
                }
                
                Toast.makeText(context, cameraKey + " Зеркало: " + (newMirror ? "Вкл" : "Выкл"), 
                        Toast.LENGTH_SHORT).show();
                AppLog.d(TAG, cameraKey + " 镜像Настройки为: " + newMirror);
            });
        }
        
        // Настройки裁剪按钮
        setupCropButtons(frame, cameraKey, textureView);
    }
    
    /**
     * Настройки裁剪按钮
     * 每 шт.方 к 点击一 раз裁剪 10 像素
     */
    private void setupCropButtons(FrameLayout frame, String cameraKey, TextureView textureView) {
        final int CROP_STEP = 10;  // 每 раз裁剪 10 像素
        
        // ========== 往里裁剪按钮（黄色) ==========
        
        // 裁剪按钮
        int cropTopBtnId = context.getResources().getIdentifier(
                "btn_crop_top_" + cameraKey, "id", context.getPackageName());
        View cropTopBtn = frame.findViewById(cropTopBtnId);
        if (cropTopBtn != null) {
            cropTopBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "top");
                int newValue = current + CROP_STEP;
                appConfig.setCameraCrop(cameraKey, "top", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 裁剪+: " + newValue + "px");
            });
        }
        
        // 裁剪按钮
        int cropBottomBtnId = context.getResources().getIdentifier(
                "btn_crop_bottom_" + cameraKey, "id", context.getPackageName());
        View cropBottomBtn = frame.findViewById(cropBottomBtnId);
        if (cropBottomBtn != null) {
            cropBottomBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "bottom");
                int newValue = current + CROP_STEP;
                appConfig.setCameraCrop(cameraKey, "bottom", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 裁剪+: " + newValue + "px");
            });
        }
        
        // 左裁剪按钮
        int cropLeftBtnId = context.getResources().getIdentifier(
                "btn_crop_left_" + cameraKey, "id", context.getPackageName());
        View cropLeftBtn = frame.findViewById(cropLeftBtnId);
        if (cropLeftBtn != null) {
            cropLeftBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "left");
                int newValue = current + CROP_STEP;
                appConfig.setCameraCrop(cameraKey, "left", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 左裁剪+: " + newValue + "px");
            });
        }
        
        // 右裁剪按钮
        int cropRightBtnId = context.getResources().getIdentifier(
                "btn_crop_right_" + cameraKey, "id", context.getPackageName());
        View cropRightBtn = frame.findViewById(cropRightBtnId);
        if (cropRightBtn != null) {
            cropRightBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "right");
                int newValue = current + CROP_STEP;
                appConfig.setCameraCrop(cameraKey, "right", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 右裁剪+: " + newValue + "px");
            });
        }
        
        // ========== 往外Восстановление按钮（绿色) ==========
        
        // Восстановление按钮
        int uncropTopBtnId = context.getResources().getIdentifier(
                "btn_uncrop_top_" + cameraKey, "id", context.getPackageName());
        View uncropTopBtn = frame.findViewById(uncropTopBtnId);
        if (uncropTopBtn != null) {
            uncropTopBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "top");
                int newValue = Math.max(0, current - CROP_STEP);
                appConfig.setCameraCrop(cameraKey, "top", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 裁剪-: " + newValue + "px");
            });
        }
        
        // Восстановление按钮
        int uncropBottomBtnId = context.getResources().getIdentifier(
                "btn_uncrop_bottom_" + cameraKey, "id", context.getPackageName());
        View uncropBottomBtn = frame.findViewById(uncropBottomBtnId);
        if (uncropBottomBtn != null) {
            uncropBottomBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "bottom");
                int newValue = Math.max(0, current - CROP_STEP);
                appConfig.setCameraCrop(cameraKey, "bottom", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 裁剪-: " + newValue + "px");
            });
        }
        
        // 左Восстановление按钮
        int uncropLeftBtnId = context.getResources().getIdentifier(
                "btn_uncrop_left_" + cameraKey, "id", context.getPackageName());
        View uncropLeftBtn = frame.findViewById(uncropLeftBtnId);
        if (uncropLeftBtn != null) {
            uncropLeftBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "left");
                int newValue = Math.max(0, current - CROP_STEP);
                appConfig.setCameraCrop(cameraKey, "left", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 左裁剪-: " + newValue + "px");
            });
        }
        
        // 右Восстановление按钮
        int uncropRightBtnId = context.getResources().getIdentifier(
                "btn_uncrop_right_" + cameraKey, "id", context.getPackageName());
        View uncropRightBtn = frame.findViewById(uncropRightBtnId);
        if (uncropRightBtn != null) {
            uncropRightBtn.setOnClickListener(v -> {
                int current = appConfig.getCameraCrop(cameraKey, "right");
                int newValue = Math.max(0, current - CROP_STEP);
                appConfig.setCameraCrop(cameraKey, "right", newValue);
                applyCrop(textureView, cameraKey);
                AppLog.d(TAG, cameraKey + " 右裁剪-: " + newValue + "px");
            });
        }
    }
    
    /**
     * Настройки车辆控制按钮
     * 前轮 и 后轮按键 选/Не 选Статус切换（互斥Выбрать)
     */
    private void setupVehicleControlButtons() {
        if (frameVehicleControl == null) return;

        Button btnFrontWheel = frameVehicleControl.findViewById(R.id.btn_front_wheel);
        Button btnRearWheel = frameVehicleControl.findViewById(R.id.btn_rear_wheel);
        ImageView ivVehicleOutline = frameVehicleControl.findViewById(R.id.iv_vehicle_outline);

        if (btnFrontWheel != null) {
            btnFrontWheel.setOnClickListener(v -> {
                boolean isSelected = btnFrontWheel.getTag() != null && (Boolean) btnFrontWheel.getTag();

                if (isSelected) {
                    // 选，Отмена选
                    isSelected = false;
                    btnFrontWheel.setTag(isSelected);
                    setButtonUnselected(btnFrontWheel);
                    // 切换 до 普通режим，ВосстановлениеПо умолчанию车辆轮廓
                    if (ivVehicleOutline != null) {
                        ivVehicleOutline.setImageResource(R.drawable.ic_vehicle_outline_normal);
                    }
                    applyNormalModeLayout();
                } else {
                    // Не 选，选前轮并Отмена后轮选
                    isSelected = true;
                    btnFrontWheel.setTag(isSelected);
                    setButtonSelected(btnFrontWheel);

                    // Отмена后轮选Статус
                    if (btnRearWheel != null) {
                        btnRearWheel.setTag(false);
                        setButtonUnselected(btnRearWheel);
                    }

                    // обновление车辆轮廓示意图，前轮显示绿色
                    if (ivVehicleOutline != null) {
                        ivVehicleOutline.setImageResource(R.drawable.ic_vehicle_outline_front);
                    }

                    // 切换 до 前轮режим
                    applyFrontWheelModeLayout();
                }

                AppLog.d(TAG, "前轮按键Статус: " + (isSelected ? "选" : "Не 选"));
            });

            // 长按事件：弹出Настройки передних колёс弹窗
            btnFrontWheel.setOnLongClickListener(v -> {
                showWheelSettingsDialog("front");
                return true;
            });
        }

        if (btnRearWheel != null) {
            btnRearWheel.setOnClickListener(v -> {
                boolean isSelected = btnRearWheel.getTag() != null && (Boolean) btnRearWheel.getTag();

                if (isSelected) {
                    // 选，Отмена选
                    isSelected = false;
                    btnRearWheel.setTag(isSelected);
                    setButtonUnselected(btnRearWheel);
                    // 切换 до 普通режим，ВосстановлениеПо умолчанию车辆轮廓
                    if (ivVehicleOutline != null) {
                        ivVehicleOutline.setImageResource(R.drawable.ic_vehicle_outline_normal);
                    }
                    applyNormalModeLayout();
                } else {
                    // Не 选，选后轮并Отмена前轮选
                    isSelected = true;
                    btnRearWheel.setTag(isSelected);
                    setButtonSelected(btnRearWheel);

                    // Отмена前轮选Статус
                    if (btnFrontWheel != null) {
                        btnFrontWheel.setTag(false);
                        setButtonUnselected(btnFrontWheel);
                    }

                    // обновление车辆轮廓示意图，后轮显示绿色
                    if (ivVehicleOutline != null) {
                        ivVehicleOutline.setImageResource(R.drawable.ic_vehicle_outline_rear);
                    }

                    // 切换 до 后轮режим
                    applyRearWheelModeLayout();
                }

                AppLog.d(TAG, "后轮按键Статус: " + (isSelected ? "选" : "Не 选"));
            });

            // 长按事件：弹出Настройки задних колёс弹窗
            btnRearWheel.setOnLongClickListener(v -> {
                showWheelSettingsDialog("rear");
                return true;
            });
        }
    }

    /**
     * 显示车轮режимНастройки弹窗
     * @param mode "front" 前轮режим, "rear" 后轮режим
     */
    private void showWheelSettingsDialog(String mode) {
        if (containerCameras == null) return;

        // 创建弹窗
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        View dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_wheel_settings, null);
        builder.setView(dialogView);

        // Настройки标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(mode.equals("front") ? "Настройки передних колёс" : "Настройки задних колёс");

        // ПолучениеТекущий容器尺寸
        int containerWidth = containerCameras.getWidth();
        int containerHeight = containerCameras.getHeight();
        
        // 普通режим画框Позиция — 前/后轮режим复用，确保画框不跳变
        int[] fp = getNormalFramePositions();

        int frontLeftWidth = 1200;
        int frontLeftHeight = 662;
        int frontLeftX = 10;
        int frontLeftY = 397;
        int frontLeftRotation = 270;
        int frontRightWidth = 1211;
        int frontRightHeight = 662;
        int frontRightX = -76;
        int frontRightY = 502;
        int frontRightRotation = 90;

        int rearLeftWidth = 1200;
        int rearLeftHeight = 662;
        int rearLeftX = 10;
        int rearLeftY = -624;
        int rearLeftRotation = 270;
        int rearRightWidth = 1298;
        int rearRightHeight = 662;
        int rearRightX = -164;
        int rearRightY = -702;
        int rearRightRotation = 90;

        // ПолучениеSeekBar и TextView引用
        android.widget.SeekBar sbLeftWidth = dialogView.findViewById(R.id.sb_left_width);
        android.widget.SeekBar sbLeftHeight = dialogView.findViewById(R.id.sb_left_height);
        android.widget.SeekBar sbLeftX = dialogView.findViewById(R.id.sb_left_x);
        android.widget.SeekBar sbLeftY = dialogView.findViewById(R.id.sb_left_y);
        android.widget.SeekBar sbLeftRotation = dialogView.findViewById(R.id.sb_left_rotation);
        android.widget.SeekBar sbRightWidth = dialogView.findViewById(R.id.sb_right_width);
        android.widget.SeekBar sbRightHeight = dialogView.findViewById(R.id.sb_right_height);
        android.widget.SeekBar sbRightX = dialogView.findViewById(R.id.sb_right_x);
        android.widget.SeekBar sbRightY = dialogView.findViewById(R.id.sb_right_y);
        android.widget.SeekBar sbRightRotation = dialogView.findViewById(R.id.sb_right_rotation);

        TextView tvLeftWidthValue = dialogView.findViewById(R.id.tv_left_width_value);
        TextView tvLeftHeightValue = dialogView.findViewById(R.id.tv_left_height_value);
        TextView tvLeftXValue = dialogView.findViewById(R.id.tv_left_x_value);
        TextView tvLeftYValue = dialogView.findViewById(R.id.tv_left_y_value);
        TextView tvLeftRotationValue = dialogView.findViewById(R.id.tv_left_rotation_value);
        TextView tvRightWidthValue = dialogView.findViewById(R.id.tv_right_width_value);
        TextView tvRightHeightValue = dialogView.findViewById(R.id.tv_right_height_value);
        TextView tvRightXValue = dialogView.findViewById(R.id.tv_right_x_value);
        TextView tvRightYValue = dialogView.findViewById(R.id.tv_right_y_value);
        TextView tvRightRotationValue = dialogView.findViewById(R.id.tv_right_rotation_value);

        // загрузкаТекущийСохранить 值
        // использование各自режим По умолчанию值
        int[] leftValues = new int[5];
        int[] rightValues = new int[5];
        
        if (mode.equals("front")) {
            leftValues[0] = appConfig.getFrontWheelLeftWidth(frontLeftWidth);
            leftValues[1] = appConfig.getFrontWheelLeftHeight(frontLeftHeight);
            leftValues[2] = appConfig.getFrontWheelLeftX(frontLeftX);
            leftValues[3] = appConfig.getFrontWheelLeftY(frontLeftY);
            leftValues[4] = appConfig.getFrontWheelLeftRotation(frontLeftRotation);
            rightValues[0] = appConfig.getFrontWheelRightWidth(frontRightWidth);
            rightValues[1] = appConfig.getFrontWheelRightHeight(frontRightHeight);
            rightValues[2] = appConfig.getFrontWheelRightX(frontRightX);
            rightValues[3] = appConfig.getFrontWheelRightY(frontRightY);
            rightValues[4] = appConfig.getFrontWheelRightRotation(frontRightRotation);
        } else {
            leftValues[0] = appConfig.getRearWheelLeftWidth(rearLeftWidth);
            leftValues[1] = appConfig.getRearWheelLeftHeight(rearLeftHeight);
            leftValues[2] = appConfig.getRearWheelLeftX(rearLeftX);
            leftValues[3] = appConfig.getRearWheelLeftY(rearLeftY);
            leftValues[4] = appConfig.getRearWheelLeftRotation(rearLeftRotation);
            rightValues[0] = appConfig.getRearWheelRightWidth(rearRightWidth);
            rightValues[1] = appConfig.getRearWheelRightHeight(rearRightHeight);
            rightValues[2] = appConfig.getRearWheelRightX(rearRightX);
            rightValues[3] = appConfig.getRearWheelRightY(rearRightY);
            rightValues[4] = appConfig.getRearWheelRightRotation(rearRightRotation);
        }

        // НастройкиSeekBar初始值 и максимум值
        // X и YПозиция 范围为-1000 до 1000，SeekBar 0-2000 应实际值-1000 до 1000
        final int POSITION_OFFSET = 1000;
        final int POSITION_MAX = 2000;
        
        sbLeftWidth.setMax(containerWidth);
        sbLeftHeight.setMax(containerHeight);
        sbLeftX.setMax(POSITION_MAX);
        sbLeftY.setMax(POSITION_MAX);
        sbLeftRotation.setMax(360);
        sbRightWidth.setMax(containerWidth);
        sbRightHeight.setMax(containerHeight);
        sbRightX.setMax(POSITION_MAX);
        sbRightY.setMax(POSITION_MAX);
        sbRightRotation.setMax(360);

        sbLeftWidth.setProgress(leftValues[0]);
        sbLeftHeight.setProgress(leftValues[1]);
        // X и YПозиция：实际值+OFFSET作为SeekBar progress
        sbLeftX.setProgress(leftValues[2] + POSITION_OFFSET);
        sbLeftY.setProgress(leftValues[3] + POSITION_OFFSET);
        sbLeftRotation.setProgress(leftValues[4]);
        sbRightWidth.setProgress(rightValues[0]);
        sbRightHeight.setProgress(rightValues[1]);
        sbRightX.setProgress(rightValues[2] + POSITION_OFFSET);
        sbRightY.setProgress(rightValues[3] + POSITION_OFFSET);
        sbRightRotation.setProgress(rightValues[4]);

        // обновлениеTextView显示（X и Y显示实际值：progress-OFFSET)
        tvLeftWidthValue.setText(String.valueOf(leftValues[0]));
        tvLeftHeightValue.setText(String.valueOf(leftValues[1]));
        tvLeftXValue.setText(String.valueOf(leftValues[2]));
        tvLeftYValue.setText(String.valueOf(leftValues[3]));
        tvLeftRotationValue.setText(leftValues[4] + "°");
        tvRightWidthValue.setText(String.valueOf(rightValues[0]));
        tvRightHeightValue.setText(String.valueOf(rightValues[1]));
        tvRightXValue.setText(String.valueOf(rightValues[2]));
        tvRightYValue.setText(String.valueOf(rightValues[3]));
        tvRightRotationValue.setText(rightValues[4] + "°");

        // 画框Позиция始终использование普通режим值，画框不跳变
        final int defaultLeftX = fp[0];
        final int defaultLeftY = fp[1];
        final int defaultLeftWidth = fp[2];
        final int defaultLeftHeight = fp[3];
        final int defaultRightX = fp[4];
        final int defaultRightY = fp[5];
        final int defaultRightWidth = fp[6];
        final int defaultRightHeight = fp[7];

        // 创建弹窗
        android.app.AlertDialog dialog = builder.create();

        // 实时预览обновление Runnable
        final Runnable previewUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (frameLeft == null || frameRight == null) return;
                
                int leftWidth = sbLeftWidth.getProgress();
                int leftHeight = sbLeftHeight.getProgress();
                // X и YПозиция：SeekBar progress-OFFSET作为实际值
                int leftX = sbLeftX.getProgress() - POSITION_OFFSET;
                int leftY = sbLeftY.getProgress() - POSITION_OFFSET;
                int leftRotation = sbLeftRotation.getProgress();
                int rightWidth = sbRightWidth.getProgress();
                int rightHeight = sbRightHeight.getProgress();
                int rightX = sbRightX.getProgress() - POSITION_OFFSET;
                int rightY = sbRightY.getProgress() - POSITION_OFFSET;
                int rightRotation = sbRightRotation.getProgress();

                // 画框不动，只调整画面纹理
                applyWheelTextureTransform(textureLeft, leftWidth, leftHeight, leftRotation, leftX, leftY);
                applyWheelTextureTransform(textureRight, rightWidth, rightHeight, rightRotation, rightX, rightY);
            }
        };

        // SeekBar变化监听器
        android.widget.SeekBar.OnSeekBarChangeListener seekBarChangeListener = new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                // обновление 应 TextView
                int id = seekBar.getId();
                if (id == R.id.sb_left_width) {
                    tvLeftWidthValue.setText(String.valueOf(progress));
                } else if (id == R.id.sb_left_height) {
                    tvLeftHeightValue.setText(String.valueOf(progress));
                } else if (id == R.id.sb_left_x) {
                    tvLeftXValue.setText(String.valueOf(progress - POSITION_OFFSET));
                } else if (id == R.id.sb_left_y) {
                    tvLeftYValue.setText(String.valueOf(progress - POSITION_OFFSET));
                } else if (id == R.id.sb_left_rotation) {
                    tvLeftRotationValue.setText(progress + "°");
                } else if (id == R.id.sb_right_width) {
                    tvRightWidthValue.setText(String.valueOf(progress));
                } else if (id == R.id.sb_right_height) {
                    tvRightHeightValue.setText(String.valueOf(progress));
                } else if (id == R.id.sb_right_x) {
                    tvRightXValue.setText(String.valueOf(progress - POSITION_OFFSET));
                } else if (id == R.id.sb_right_y) {
                    tvRightYValue.setText(String.valueOf(progress - POSITION_OFFSET));
                } else if (id == R.id.sb_right_rotation) {
                    tvRightRotationValue.setText(progress + "°");
                }

                // 实时обновление预览
                previewUpdateRunnable.run();
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        };

        // Настройки所有SeekBar 监听器
        sbLeftWidth.setOnSeekBarChangeListener(seekBarChangeListener);
        sbLeftHeight.setOnSeekBarChangeListener(seekBarChangeListener);
        sbLeftX.setOnSeekBarChangeListener(seekBarChangeListener);
        sbLeftY.setOnSeekBarChangeListener(seekBarChangeListener);
        sbLeftRotation.setOnSeekBarChangeListener(seekBarChangeListener);
        sbRightWidth.setOnSeekBarChangeListener(seekBarChangeListener);
        sbRightHeight.setOnSeekBarChangeListener(seekBarChangeListener);
        sbRightX.setOnSeekBarChangeListener(seekBarChangeListener);
        sbRightY.setOnSeekBarChangeListener(seekBarChangeListener);
        sbRightRotation.setOnSeekBarChangeListener(seekBarChangeListener);

        // НастройкиСохранить按钮点击事件
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> {
            int leftWidth = sbLeftWidth.getProgress();
            int leftHeight = sbLeftHeight.getProgress();
            // X и YПозиция：SeekBar progress-OFFSET作为实际值
            int leftX = sbLeftX.getProgress() - POSITION_OFFSET;
            int leftY = sbLeftY.getProgress() - POSITION_OFFSET;
            int leftRotation = sbLeftRotation.getProgress();
            int rightWidth = sbRightWidth.getProgress();
            int rightHeight = sbRightHeight.getProgress();
            int rightX = sbRightX.getProgress() - POSITION_OFFSET;
            int rightY = sbRightY.getProgress() - POSITION_OFFSET;
            int rightRotation = sbRightRotation.getProgress();

            if (mode.equals("front")) {
                appConfig.setFrontWheelLeftViewParams(leftWidth, leftHeight, leftX, leftY, leftRotation);
                appConfig.setFrontWheelRightViewParams(rightWidth, rightHeight, rightX, rightY, rightRotation);
                Toast.makeText(context, "Настройки передних колёс сохранены", Toast.LENGTH_SHORT).show();
            } else {
                appConfig.setRearWheelLeftViewParams(leftWidth, leftHeight, leftX, leftY, leftRotation);
                appConfig.setRearWheelRightViewParams(rightWidth, rightHeight, rightX, rightY, rightRotation);
                Toast.makeText(context, "Настройки задних колёс сохранены", Toast.LENGTH_SHORT).show();
            }

            dialog.dismiss();
        });

        // НастройкиСброс按钮点击事件
        Button btnReset = dialogView.findViewById(R.id.btn_reset);
        btnReset.setOnClickListener(v -> {
            // Сброс为 应режим По умолчанию值
            int resetLeftWidth, resetLeftHeight, resetLeftX, resetLeftY, resetLeftRotation;
            int resetRightWidth, resetRightHeight, resetRightX, resetRightY, resetRightRotation;
            
            if (mode.equals("front")) {
                resetLeftWidth = frontLeftWidth;
                resetLeftHeight = frontLeftHeight;
                resetLeftX = frontLeftX;
                resetLeftY = frontLeftY;
                resetLeftRotation = frontLeftRotation;
                resetRightWidth = frontRightWidth;
                resetRightHeight = frontRightHeight;
                resetRightX = frontRightX;
                resetRightY = frontRightY;
                resetRightRotation = frontRightRotation;
            } else {
                resetLeftWidth = rearLeftWidth;
                resetLeftHeight = rearLeftHeight;
                resetLeftX = rearLeftX;
                resetLeftY = rearLeftY;
                resetLeftRotation = rearLeftRotation;
                resetRightWidth = rearRightWidth;
                resetRightHeight = rearRightHeight;
                resetRightX = rearRightX;
                resetRightY = rearRightY;
                resetRightRotation = rearRightRotation;
            }

            sbLeftWidth.setProgress(resetLeftWidth);
            sbLeftHeight.setProgress(resetLeftHeight);
            // X и YПозиция：实际值+OFFSET作为SeekBar progress
            sbLeftX.setProgress(resetLeftX + POSITION_OFFSET);
            sbLeftY.setProgress(resetLeftY + POSITION_OFFSET);
            sbLeftRotation.setProgress(resetLeftRotation);
            sbRightWidth.setProgress(resetRightWidth);
            sbRightHeight.setProgress(resetRightHeight);
            sbRightX.setProgress(resetRightX + POSITION_OFFSET);
            sbRightY.setProgress(resetRightY + POSITION_OFFSET);
            sbRightRotation.setProgress(resetRightRotation);

            // обновлениеTextView（X и Y显示实际值)
            tvLeftWidthValue.setText(String.valueOf(resetLeftWidth));
            tvLeftHeightValue.setText(String.valueOf(resetLeftHeight));
            tvLeftXValue.setText(String.valueOf(resetLeftX));
            tvLeftYValue.setText(String.valueOf(resetLeftY));
            tvLeftRotationValue.setText(resetLeftRotation + "°");
            tvRightWidthValue.setText(String.valueOf(resetRightWidth));
            tvRightHeightValue.setText(String.valueOf(resetRightHeight));
            tvRightXValue.setText(String.valueOf(resetRightX));
            tvRightYValue.setText(String.valueOf(resetRightY));
            tvRightRotationValue.setText(resetRightRotation + "°");

            // 实时ПриложениеСброс后 值
            previewUpdateRunnable.run();

            Toast.makeText(context, "Сброшено до значений по умолчанию", Toast.LENGTH_SHORT).show();
        });

        // Настройки弹窗背景透明度为15%
        dialog.setOnShowListener(d -> {
            View rootView = dialog.getWindow().getDecorView();
            rootView.setBackgroundColor(android.graphics.Color.parseColor("#26000000"));
        });

        // 弹窗Закрыто时Восстановление原始布局（Если Не Сохранить)
        dialog.setOnDismissListener(d -> {
            // 重新ПриложениеТекущийрежим Сохранить值
            if (mode.equals("front")) {
                Button btnFrontWheel = frameVehicleControl.findViewById(R.id.btn_front_wheel);
                if (btnFrontWheel != null && btnFrontWheel.getTag() != null && (Boolean) btnFrontWheel.getTag()) {
                    applyFrontWheelModeLayout();
                } else {
                    applyNormalModeLayout();
                }
            } else {
                Button btnRearWheel = frameVehicleControl.findViewById(R.id.btn_rear_wheel);
                if (btnRearWheel != null && btnRearWheel.getTag() != null && (Boolean) btnRearWheel.getTag()) {
                    applyRearWheelModeLayout();
                } else {
                    applyNormalModeLayout();
                }
            }
        });

        dialog.show();
    }

    /**
     * Приложение前轮режим布局
     * 初始值 и 普通режим相同
     */
    private void applyFrontWheelModeLayout() {
        if (frameLeft == null || frameRight == null) return;

        int containerWidth = containerCameras.getWidth();
        int containerHeight = containerCameras.getHeight();

        AppLog.d(TAG, "前轮режим - 容器尺寸: " + containerWidth + "x" + containerHeight);

        if (containerWidth == 0 || containerHeight == 0) {
            AppLog.e(TAG, "前轮режим - 容器尺寸为0，延迟重试");
            containerCameras.post(this::applyFrontWheelModeLayout);
            return;
        }

        if (frameVehicleControl != null) {
            frameVehicleControl.setVisibility(View.VISIBLE);
        }
        frameLeft.setVisibility(View.VISIBLE);
        frameRight.setVisibility(View.VISIBLE);

        // 画框完全不动，只операция画面纹理

        // 前轮режим画面По умолчанию值（相 画框内偏移 + Поворот )
        int leftRotation  = appConfig.getFrontWheelLeftRotation(270);
        int rightRotation = appConfig.getFrontWheelRightRotation(90);
        int leftWidth  = appConfig.getFrontWheelLeftWidth(1200);
        int leftHeight = appConfig.getFrontWheelLeftHeight(662);
        int leftX      = appConfig.getFrontWheelLeftX(10);
        int leftY      = appConfig.getFrontWheelLeftY(397);
        int rightWidth  = appConfig.getFrontWheelRightWidth(1211);
        int rightHeight = appConfig.getFrontWheelRightHeight(662);
        int rightX      = appConfig.getFrontWheelRightX(-76);
        int rightY      = appConfig.getFrontWheelRightY(502);

        applyWheelTextureTransform(textureLeft, leftWidth, leftHeight, leftRotation, leftX, leftY);
        applyWheelTextureTransform(textureRight, rightWidth, rightHeight, rightRotation, rightX, rightY);

        AppLog.d(TAG, "前轮режим布局Приложение - 左: (" + leftX + "," + leftY + ") " + leftWidth + "x" + leftHeight
                + " rot=" + leftRotation + ", Пр: (" + rightX + "," + rightY + ") " + rightWidth + "x" + rightHeight
                + " rot=" + rightRotation);
    }

    /**
     * Приложение后轮режим布局
     * 初始值 и 普通режим相同
     */
    private void applyRearWheelModeLayout() {
        if (frameLeft == null || frameRight == null) return;

        int containerWidth = containerCameras.getWidth();
        int containerHeight = containerCameras.getHeight();

        AppLog.d(TAG, "后轮режим - 容器尺寸: " + containerWidth + "x" + containerHeight);

        if (containerWidth == 0 || containerHeight == 0) {
            AppLog.e(TAG, "后轮режим - 容器尺寸为0，延迟重试");
            containerCameras.post(this::applyRearWheelModeLayout);
            return;
        }

        if (frameVehicleControl != null) {
            frameVehicleControl.setVisibility(View.VISIBLE);
        }
        frameLeft.setVisibility(View.VISIBLE);
        frameRight.setVisibility(View.VISIBLE);

        // 画框完全不动，只операция画面纹理

        // 后轮режим画面По умолчанию值（相 画框内偏移 + Поворот )
        int leftRotation  = appConfig.getRearWheelLeftRotation(270);
        int rightRotation = appConfig.getRearWheelRightRotation(90);
        int leftWidth  = appConfig.getRearWheelLeftWidth(1200);
        int leftHeight = appConfig.getRearWheelLeftHeight(662);
        int leftX      = appConfig.getRearWheelLeftX(10);
        int leftY      = appConfig.getRearWheelLeftY(-624);
        int rightWidth  = appConfig.getRearWheelRightWidth(1298);
        int rightHeight = appConfig.getRearWheelRightHeight(662);
        int rightX      = appConfig.getRearWheelRightX(-164);
        int rightY      = appConfig.getRearWheelRightY(-702);

        applyWheelTextureTransform(textureLeft, leftWidth, leftHeight, leftRotation, leftX, leftY);
        applyWheelTextureTransform(textureRight, rightWidth, rightHeight, rightRotation, rightX, rightY);

        AppLog.d(TAG, "后轮режим布局Приложение - 左: (" + leftX + "," + leftY + ") " + leftWidth + "x" + leftHeight
                + " rot=" + leftRotation + ", Пр: (" + rightX + "," + rightY + ") " + rightWidth + "x" + rightHeight
                + " rot=" + rightRotation);
    }

    /**
     * 轮胎режим：纯 View transform，不碰 LayoutParams，不触发 requestLayout。
     * Используется setTranslationX/Y вместо setX/Y, чтобы избежать гонки с асинхронным layout pass:
     * setX(x) внутри делает setTranslationX(x - mLeft), и если mLeft ещё старый — результат неверный;
     * setTranslationX(x) устанавливает значение напрямую, после layout pass mLeft обнулится, итого позиция = 0 + x = x.
     */
    private void applyWheelTextureTransform(TextureView tv, int w, int h, int rotation, int x, int y) {
        if (tv == null) return;
        tv.setRotation(rotation);
        if (rotation == 90 || rotation == 270) {
            float scale = Math.max((float) w / h, (float) h / w);
            float curSx = tv.getScaleX();
            tv.setScaleX(curSx < 0 ? -scale : scale);
            tv.setScaleY(scale);
        } else {
            float curSx = tv.getScaleX();
            tv.setScaleX(curSx < 0 ? -1f : 1f);
            tv.setScaleY(1f);
        }
        tv.setTranslationX(x);
        tv.setTranslationY(y);
    }

    /**
     * Выход轮胎режим：Сброс transform 属性 до 普通режим。
     */
    private void resetWheelTextureTransform(TextureView tv, int normalRotation) {
        if (tv == null) return;
        applyRotationWithScale(tv, normalRotation);
        tv.setTranslationX(0);
        tv.setTranslationY(0);
    }

    /**
     * Приложение普通режим布局（По умолчаниюрежим)
     * 普通режим保持车辆控制区域可见
     */
    private void applyNormalModeLayout() {
        if (frameLeft == null || frameRight == null) return;

        if (frameVehicleControl != null) {
            frameVehicleControl.setVisibility(View.VISIBLE);
        }
        frameLeft.setVisibility(View.VISIBLE);
        frameRight.setVisibility(View.VISIBLE);

        int leftRotation = appConfig.getNormalLeftRotation(0);
        int rightRotation = appConfig.getNormalRightRotation(0);

        // Восстановление画面 до 普通режим：Сброс所有 transform 属性
        resetWheelTextureTransform(textureLeft, leftRotation);
        resetWheelTextureTransform(textureRight, rightRotation);

        AppLog.d(TAG, "普通режим布局Приложение");
    }

    /**
     * Получение普通режим左右画框 Позиция и 大小（ от  appConfig или计算По умолчанию值)。
     * 前/后轮режим复用这 групп值，确保画框Позиция不跳变。
     * @return int[8]: leftX, leftY, leftW, leftH, rightX, rightY, rightW, rightH
     */
    private int[] getNormalFramePositions() {
        int cw = containerCameras.getWidth();
        int ch = containerCameras.getHeight();
        int gap = dp(GAP_DP);
        int vcw = 280;
        int topH = (ch - gap) / 2;
        int botH = ch - topH - gap;
        int botY = topH + gap;
        int botContentW = cw - gap * 2 - vcw;
        int defLW = botContentW / 2;
        int defRW = botContentW - defLW;
        int defH  = botH;
        int defLX = 0;
        int defLY = botY;
        int defRX = defLW + gap + vcw + gap;
        int defRY = botY;

        return new int[] {
            appConfig.getNormalLeftX(defLX),
            appConfig.getNormalLeftY(defLY),
            appConfig.getNormalLeftWidth(defLW),
            appConfig.getNormalLeftHeight(defH),
            appConfig.getNormalRightX(defRX),
            appConfig.getNormalRightY(defRY),
            appConfig.getNormalRightWidth(defRW),
            appConfig.getNormalRightHeight(defH)
        };
    }

    /**
     * Настройки按钮为选Статус
     */
    private void setButtonSelected(Button button) {
        button.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#007AFF")));
    }

    /**
     * Настройки按钮为Не 选Статус
     */
    private void setButtonUnselected(Button button) {
        button.setTextColor(android.graphics.Color.parseColor("#808080"));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#00000000")));
    }

    /**
     * Приложение裁剪效果
     * использование clipBounds 来裁剪 TextureView  显示区域
     */
    private void applyCrop(TextureView textureView, String cameraKey) {
        applyCropWithRetry(textureView, cameraKey, 0);
    }
    
    /**
     * 带重试 裁剪Приложение
     * @param retryCount Текущий重试 раз数
     */
    private void applyCropWithRetry(TextureView textureView, String cameraKey, int retryCount) {
        if (textureView == null) return;
        
        // 最多重试 10  раз，每 раз延迟 100ms
        final int MAX_RETRY = 10;
        
        int cropTop = appConfig.getCameraCrop(cameraKey, "top");
        int cropBottom = appConfig.getCameraCrop(cameraKey, "bottom");
        int cropLeft = appConfig.getCameraCrop(cameraKey, "left");
        int cropRight = appConfig.getCameraCrop(cameraKey, "right");
        
        // Если 没有裁剪конфигурация，直接返回
        if (cropTop == 0 && cropBottom == 0 && cropLeft == 0 && cropRight == 0) {
            textureView.setClipBounds(null);
            return;
        }
        
        int width = textureView.getWidth();
        int height = textureView.getHeight();
        
        if (width <= 0 || height <= 0) {
            // 视图尚Не 布局завершение，延迟Приложение
            if (retryCount < MAX_RETRY) {
                textureView.postDelayed(() -> applyCropWithRetry(textureView, cameraKey, retryCount + 1), 100);
                AppLog.d(TAG, cameraKey + " 裁剪ожидание布局，重试 " + (retryCount + 1));
            } else {
                AppLog.w(TAG, cameraKey + " 裁剪ПриложениеОшибка：视图尺寸为 0，达максимум重试 раз数");
            }
            return;
        }
        
        // 计算裁剪区域
        int left = cropLeft;
        int top = cropTop;
        int right = width - cropRight;
        int bottom = height - cropBottom;
        
        // 确保裁剪区域действует
        if (left >= right || top >= bottom) {
            // 裁剪区域недействительно，Сброс
            appConfig.resetCameraCrop(cameraKey);
            textureView.setClipBounds(null);
            Toast.makeText(context, "Обрезка слишком большая, сброшена", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Приложение裁剪
        android.graphics.Rect clipBounds = new android.graphics.Rect(left, top, right, bottom);
        textureView.setClipBounds(clipBounds);
        
        AppLog.d(TAG, cameraKey + " 裁剪ПриложениеУспешно: left=" + cropLeft + ", top=" + cropTop + 
                ", right=" + cropRight + ", bottom=" + cropBottom + " (Размер вида: " + width + "x" + height + ")");
    }
    
    /**
     * 立т.е.Приложение所有裁剪（用于布局Восстановление时， 容器显示до调用)
     */
    private void applyAllCropsImmediately() {
        if (textureFront != null) applyCrop(textureFront, "front");
        if (textureBack != null) applyCrop(textureBack, "back");
        if (textureLeft != null) applyCrop(textureLeft, "left");
        if (textureRight != null) applyCrop(textureRight, "right");
        AppLog.d(TAG, "立т.е.Приложение裁剪конфигурация");
    }
    
    /**
     * Приложение所有Камера Сохранить 裁剪конфигурация
     * использование更长 延迟确保 TextureView 经有正确 尺寸
     * только 没有通过 restoreLayout Восстановление时использование
     */
    private void applySavedCrops() {
        // Если 经有布局数据（会  restoreLayout Приложение裁剪)，则跳过
        String savedData = appConfig.getCustomLayoutData();
        if (savedData != null && !savedData.isEmpty()) {
            AppLog.d(TAG, "布局数据существует，裁剪将 布局Восстановление时Приложение");
            return;
        }
        
        // 延迟 500ms 后Приложение裁剪，确保Камера预览经Вкл始
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (textureFront != null) applyCrop(textureFront, "front");
            if (textureBack != null) applyCrop(textureBack, "back");
            if (textureLeft != null) applyCrop(textureLeft, "left");
            if (textureRight != null) applyCrop(textureRight, "right");
            AppLog.d(TAG, "触发裁剪конфигурацияВосстановление（无布局数据режим)");
        }, 500);
    }
    
    /**
     * Настройки编辑控制面板 按钮
     */
    private void setupEditControlButtons() {
        if (editControlsView == null) return;
        
        // Сохранить按钮
        Button btnSave = editControlsView.findViewById(
                context.getResources().getIdentifier("btn_save_layout", "id", context.getPackageName()));
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                saveLayout();
                Toast.makeText(context, "Макет сохранён", Toast.LENGTH_SHORT).show();
            });
        }
        
        // Сброс按钮
        Button btnReset = editControlsView.findViewById(
                context.getResources().getIdentifier("btn_reset_layout", "id", context.getPackageName()));
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                resetLayout();
                Toast.makeText(context, "Макет сброшен", Toast.LENGTH_SHORT).show();
            });
        }
        
        // перезагрузка按钮
        Button btnRestart = editControlsView.findViewById(
                context.getResources().getIdentifier("btn_restart_app", "id", context.getPackageName()));
        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                restartApp();
            });
        }
        
        // 按钮容器缩小
        Button btnButtonsShrink = editControlsView.findViewById(
                context.getResources().getIdentifier("btn_buttons_shrink", "id", context.getPackageName()));
        if (btnButtonsShrink != null) {
            btnButtonsShrink.setOnClickListener(v -> adjustButtonSize(false));
        }
        
        // 按钮容器放大
        Button btnButtonsEnlarge = editControlsView.findViewById(
                context.getResources().getIdentifier("btn_buttons_enlarge", "id", context.getPackageName()));
        if (btnButtonsEnlarge != null) {
            btnButtonsEnlarge.setOnClickListener(v -> adjustButtonSize(true));
        }
        
        // 按钮方 к 切换
        Button btnButtonsRotate = editControlsView.findViewById(
                context.getResources().getIdentifier("btn_buttons_rotate", "id", context.getPackageName()));
        if (btnButtonsRotate != null) {
            btnButtonsRotate.setOnClickListener(v -> {
                String currentOrientation = appConfig.getCustomButtonOrientation();
                String newOrientation = AppConfig.BUTTON_ORIENTATION_VERTICAL.equals(currentOrientation) ?
                        AppConfig.BUTTON_ORIENTATION_HORIZONTAL : AppConfig.BUTTON_ORIENTATION_VERTICAL;
                appConfig.setCustomButtonOrientation(newOrientation);
                
                // Уведомление监听器重新загрузка按钮布局
                if (buttonLayoutChangeListener != null) {
                    buttonLayoutChangeListener.onButtonLayoutChange(newOrientation);
                }
                
                Toast.makeText(context, "Направление кнопок: " + 
                        (newOrientation.equals(AppConfig.BUTTON_ORIENTATION_VERTICAL) ? "Вертикальная" : "Горизонтальная"), 
                        Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    /**
     * Настройки单 шт.Камера容器 自由操控
     */
    private void setupCameraFrame(FrameLayout frame, String cameraId) {
        // 直接использование整 шт.frame作为拖动区域（不необходимо角标)
        frame.setOnTouchListener(new DragTouchListener(frame, cameraId));
        
        // 查找缩小按钮
        int shrinkBtnId = context.getResources().getIdentifier(
                "btn_shrink_" + cameraId, "id", context.getPackageName());
        View shrinkBtn = frame.findViewById(shrinkBtnId);
        if (shrinkBtn != null) {
            shrinkBtn.setOnClickListener(v -> adjustSize(frame, cameraId, false));
        }
        
        // 查找放大按钮
        int enlargeBtnId = context.getResources().getIdentifier(
                "btn_enlarge_" + cameraId, "id", context.getPackageName());
        View enlargeBtn = frame.findViewById(enlargeBtnId);
        if (enlargeBtn != null) {
            enlargeBtn.setOnClickListener(v -> adjustSize(frame, cameraId, true));
        }
        
        // 查找隐藏按钮
        int hideBtnId = context.getResources().getIdentifier(
                "btn_hide_" + cameraId, "id", context.getPackageName());
        View hideBtn = frame.findViewById(hideBtnId);
        if (hideBtn != null) {
            hideBtn.setOnClickListener(v -> toggleVisibility(frame, cameraId));
        }
    }
    
    /**
     * Настройки按钮容器 自由操控
     */
    private void setupButtonContainer(ViewGroup container) {
        // 按钮容器可以整体拖动
        // необходимо先移除 layout_gravity，否则ПозицияНастройки会 覆盖
        if (container.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
            // 移除 gravity，改为использование绝 Позиция
            params.gravity = android.view.Gravity.NO_GRAVITY;
            // Если 宽度  match_parent，改为 wrap_content 以поддержка拖动
            if (params.width == FrameLayout.LayoutParams.MATCH_PARENT) {
                params.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            }
            container.setLayoutParams(params);
        }
        container.setOnTouchListener(new DragTouchListener(container, "buttons"));
    }
    
    /**
     * 调整按钮容器大小
     */
    public void adjustButtonSize(boolean enlarge) {
        if (buttonContainer != null) {
            adjustSize(buttonContainer, "buttons", enlarge);
        }
    }

    /**
     * Настройки编辑режим
     * @param enabled true 显示编辑控制按钮，false 隐藏
     */
    public void setEditMode(boolean enabled) {
        this.editModeEnabled = enabled;
        
        // 显示/隐藏编辑控制视图
        if (editControlsView != null) {
            editControlsView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        
        // 显示/隐藏各 шт.Камера 控制按钮
        setControlButtonsVisibility(frameFront, "front", enabled);
        setControlButtonsVisibility(frameBack, "back", enabled);
        setControlButtonsVisibility(frameLeft, "left", enabled);
        setControlButtonsVisibility(frameRight, "right", enabled);
        
        AppLog.d(TAG, "编辑режим: " + (enabled ? "Вкл启" : "Закрыть"));
    }
    
    /**
     * Настройки控制按钮 可见性
     */
    private void setControlButtonsVisibility(FrameLayout frame, String cameraId, boolean visible) {
        if (frame == null) return;
        
        int visibility = visible ? View.VISIBLE : View.GONE;
        
        // 控制按钮容器（controls_front, controls_back 等)
        int controlsId = context.getResources().getIdentifier(
                "controls_" + cameraId, "id", context.getPackageName());
        View controlsContainer = frame.findViewById(controlsId);
        if (controlsContainer != null) {
            controlsContainer.setVisibility(visibility);
            AppLog.d(TAG, "控制按钮容器 controls_" + cameraId + " 可见性: " + visible);
        } else {
            AppLog.w(TAG, "Не 找 до 控制按钮容器: controls_" + cameraId);
        }
        
        // 拖动手柄（Если 有 话)
        int dragHandleId = context.getResources().getIdentifier(
                "drag_handle_" + cameraId, "id", context.getPackageName());
        View dragHandle = frame.findViewById(dragHandleId);
        if (dragHandle != null) {
            dragHandle.setVisibility(visibility);
        }
    }

    /**
     * 切换视图可见性
     * использование透明度而不 GONE，避免TextureViewинициализация问题
     */
    private void toggleVisibility(View view, String id) {
        boolean isCurrentlyVisible = layoutData.isVisible(id);
        
        if (isCurrentlyVisible) {
            // 隐藏
            view.setAlpha(0f);
            view.setClickable(false);
            view.setFocusable(false);
            layoutData.setVisible(id, false);
            AppLog.d(TAG, id + " 隐藏");
        } else {
            // 显示
            view.setAlpha(1f);
            view.setClickable(true);
            view.setFocusable(true);
            layoutData.setVisible(id, true);
            AppLog.d(TAG, id + " 显示");
        }
    }

    /**
     * 调整视图大小
     * @param view 目标视图
     * @param id 视图ID
     * @param enlarge true=放大, false=缩小
     */
    private void adjustSize(View view, String id, boolean enlarge) {
        float currentScale = layoutData.getScale(id);
        float newScale;
        
        if (enlarge) {
            newScale = Math.min(currentScale + SCALE_STEP, MAX_SCALE);
        } else {
            newScale = Math.max(currentScale - SCALE_STEP, MIN_SCALE);
        }
        
        if (newScale != currentScale) {
            layoutData.setScale(id, newScale);
            applyScale(view, newScale);
            AppLog.d(TAG, id + " 缩放: " + Math.round(newScale * 100) + "%");
        }
    }
    
    /**
     * Приложение缩放比例
     *  от 左角Вкл始缩放，使角标始终保持 左角Позиция
     * 同时反 к 缩放控制按钮使其保持固定大小
     */
    private void applyScale(View view, float scale) {
        // Настройки缩放心点为左角 (0, 0)
        // 这样缩放时左角保持不动，角标自然 正确Позиция
        view.setPivotX(0);
        view.setPivotY(0);
        view.setScaleX(scale);
        view.setScaleY(scale);
        
        // 反 к 缩放控制按钮，使其保持固定大小
        if (view instanceof FrameLayout) {
            compensateControlButtonsScale((FrameLayout) view, scale);
        }
    }
    
    /**
     * 反 к 缩放控制按钮 и 角标，使其 画面缩放时保持固定大小
     */
    private void compensateControlButtonsScale(FrameLayout frame, float parentScale) {
        // 查找控制按钮容器 и 角标
        for (int i = 0; i < frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            String resourceName = "";
            try {
                resourceName = context.getResources().getResourceEntryName(child.getId());
            } catch (Exception e) {
                continue;
            }
            
            // 只反 к 缩放控制按钮，角标跟随画面缩放（小画面配小角标更直观)
            if (resourceName.startsWith("controls_")) {
                float compensateScale = 1.0f / parentScale;
                child.setScaleX(compensateScale);
                child.setScaleY(compensateScale);
                AppLog.d(TAG, "控制按钮补偿缩放: " + resourceName + " -> " + Math.round(compensateScale * 100) + "%");
            }
        }
    }

    /**
     * СохранитьТекущий布局
     */
    public void saveLayout() {
        // обновление布局数据 Позиция и 尺寸Информация
        saveViewLayout(frameFront, "front");
        saveViewLayout(frameBack, "back");
        saveViewLayout(frameLeft, "left");
        saveViewLayout(frameRight, "right");
        saveViewLayout(frameVehicleControl, "vehicle");
        saveViewLayout(buttonContainer, "buttons");
        
        // Сохранить裁剪数据
        saveCropData("front");
        saveCropData("back");
        saveCropData("left");
        saveCropData("right");
        
        // Сохранить до конфигурация
        appConfig.setCustomLayoutData(layoutData.toJson());
        appConfig.setCustomLayoutVersion(LAYOUT_VERSION);
        AppLog.d(TAG, "布局Сохранить: " + layoutData.toJson());
    }
    
    /**
     * Сохранить单 шт.Камера 裁剪数据 до 布局数据
     */
    private void saveCropData(String cameraKey) {
        int top = appConfig.getCameraCrop(cameraKey, "top");
        int bottom = appConfig.getCameraCrop(cameraKey, "bottom");
        int left = appConfig.getCameraCrop(cameraKey, "left");
        int right = appConfig.getCameraCrop(cameraKey, "right");
        layoutData.setCrop(cameraKey, top, bottom, left, right);
    }
    
    /**
     *  от 布局数据Восстановление裁剪конфигурация до  AppConfig
     */
    private void restoreCropDataFromLayout() {
        restoreCropForCamera("front");
        restoreCropForCamera("back");
        restoreCropForCamera("left");
        restoreCropForCamera("right");
    }
    
    /**
     * Восстановление单 шт.Камера 裁剪数据
     */
    private void restoreCropForCamera(String cameraKey) {
        if (layoutData.hasCrop(cameraKey)) {
            int top = layoutData.getCrop(cameraKey, "top");
            int bottom = layoutData.getCrop(cameraKey, "bottom");
            int left = layoutData.getCrop(cameraKey, "left");
            int right = layoutData.getCrop(cameraKey, "right");
            
            if (top >= 0) appConfig.setCameraCrop(cameraKey, "top", top);
            if (bottom >= 0) appConfig.setCameraCrop(cameraKey, "bottom", bottom);
            if (left >= 0) appConfig.setCameraCrop(cameraKey, "left", left);
            if (right >= 0) appConfig.setCameraCrop(cameraKey, "right", right);
            
            AppLog.d(TAG, cameraKey + " 裁剪Восстановление: top=" + top + ", bottom=" + bottom + 
                    ", left=" + left + ", right=" + right);
        }
    }
    
    /**
     * Сохранить单 шт.视图 布局数据（Позиция и 尺寸)
     */
    private void saveViewLayout(View view, String id) {
        if (view == null) return;
        
        // СохранитьПозиция（四舍五入为整数，避免浮点精度问题)
        float x = Math.round(view.getTranslationX());
        float y = Math.round(view.getTranslationY());
        layoutData.setPosition(id, x, y);
        
        // Сохранить尺寸（考虑缩放后 实际尺寸)
        int width = view.getWidth();
        int height = view.getHeight();
        if (width > 0 && height > 0) {
            layoutData.setSize(id, width, height);
            AppLog.d(TAG, id + " Сохранить尺寸: " + width + "x" + height + " Позиция: (" + x + ", " + y + ")");
        }
    }

    /**
     * Сброс布局 до По умолчаниюСтатус
     */
    public void resetLayout() {
        layoutData = new LayoutData();
        appConfig.clearCustomLayoutData();
        appConfig.setCustomLayoutVersion(LAYOUT_VERSION);

        // Сброс所有视图缩放 и Поворот （FrameLayout)
        resetViewTransform(frameFront);
        resetViewTransform(frameBack);
        resetViewTransform(frameLeft);
        resetViewTransform(frameRight);
        resetViewTransform(buttonContainer);
        
        // Сброс所有裁剪конфигурация
        resetAllCrops();
        
        // СбросКамераПоворот  и 镜像конфигурация
        resetAllRotationAndMirror();
        
        // 重新Настройки初始Позиция（四宫格/双/单)
        if (containerCameras != null) {
            setupDefaultPositions();
        }
        
        // 显示所有视图
        showAllViews();
        
        AppLog.d(TAG, "Макет сброшен");
    }
    
    /**
     * перезагрузкаПриложение
     */
    /**
     * 重载界面（重新创建 Activity)
     */
    private void restartApp() {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;

            Toast.makeText(context, "Перезагрузка интерфейса...", Toast.LENGTH_SHORT).show();
            // 清掉 Holder  旧 CameraManager，避免新 Activity 复用处于不一致Статус 实例
            com.kooo.evcam.camera.CameraManagerHolder.getInstance().setCameraManager(null);
            activity.recreate();
        }
    }
    
    /**
     * Сброс所有Камера Поворот  и 镜像конфигурация
     */
    private void resetAllRotationAndMirror() {
        // Сброс AppConfig  конфигурация
        appConfig.setCameraRotation("front", 0);
        appConfig.setCameraRotation("back", 0);
        appConfig.setCameraRotation("left", 0);
        appConfig.setCameraRotation("right", 0);
        appConfig.setCameraMirror("front", false);
        appConfig.setCameraMirror("back", false);
        appConfig.setCameraMirror("left", false);
        appConfig.setCameraMirror("right", false);
        
        // Сброс TextureView  Поворот  и 缩放
        resetTextureViewTransform(textureFront);
        resetTextureViewTransform(textureBack);
        resetTextureViewTransform(textureLeft);
        resetTextureViewTransform(textureRight);
        
        AppLog.d(TAG, "КамераПоворот  и 镜像Сброс");
    }
    
    /**
     * Сброс单 шт. TextureView  变换
     */
    private void resetTextureViewTransform(TextureView textureView) {
        if (textureView == null) return;
        textureView.setRotation(0f);
        textureView.setScaleX(1.0f);
        textureView.setScaleY(1.0f);
    }
    
    /**
     * Сброс所有Камера 裁剪конфигурация
     */
    private void resetAllCrops() {
        appConfig.resetCameraCrop("front");
        appConfig.resetCameraCrop("back");
        appConfig.resetCameraCrop("left");
        appConfig.resetCameraCrop("right");
        
        // очистка裁剪效果
        if (textureFront != null) textureFront.setClipBounds(null);
        if (textureBack != null) textureBack.setClipBounds(null);
        if (textureLeft != null) textureLeft.setClipBounds(null);
        if (textureRight != null) textureRight.setClipBounds(null);
    }
    
    /**
     * Сброс单 шт.视图 变换（缩放、Поворот )
     */
    private void resetViewTransform(View view) {
        if (view == null) return;
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setRotation(0f);
        
        // Если   FrameLayout，такженеобходимоСбросВнутреннее控制按钮容器 缩放
        if (view instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) view;
            for (int i = 0; i < frame.getChildCount(); i++) {
                View child = frame.getChildAt(i);
                try {
                    String resourceName = context.getResources().getResourceEntryName(child.getId());
                    if (resourceName.startsWith("controls_")) {
                        child.setScaleX(1.0f);
                        child.setScaleY(1.0f);
                        AppLog.d(TAG, "Сброс控制按钮容器缩放: " + resourceName);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }
    
    /**
     * НастройкиПо умолчанию КамераПозиция
     */
    private void setupDefaultPositions() {
        int containerWidth = containerCameras.getWidth();
        int containerHeight = containerCameras.getHeight();

        if (containerWidth == 0 || containerHeight == 0) {
            return;
        }

        int side = dp(SIDE_MARGIN_DP);
        int gap = dp(GAP_DP);

        if (cameraCount == 1) {
            if (frameFront != null) {
                frameFront.setVisibility(View.VISIBLE);
                setViewPosition(frameFront, side, 0, containerWidth - side * 2, containerHeight);
            }
        } else if (cameraCount == 2) {
            int camW = (containerWidth - side * 2 - gap) / 2;
            if (frameFront != null) {
                frameFront.setVisibility(View.VISIBLE);
                setViewPosition(frameFront, side, 0, camW, containerHeight);
            }
            if (frameBack != null) {
                frameBack.setVisibility(View.VISIBLE);
                setViewPosition(frameBack, side + camW + gap, 0, camW, containerHeight);
            }
        } else {
            // 4摄: все视图прижаты к краям контейнера, только gap между ними
            int topH = (containerHeight - gap) / 2;
            int botH = containerHeight - topH - gap;
            int topW = (containerWidth - gap) / 2;

            if (frameFront != null) {
                frameFront.setVisibility(View.VISIBLE);
                setViewPosition(frameFront, 0, 0, topW, topH);
            }
            if (frameBack != null) {
                frameBack.setVisibility(View.VISIBLE);
                setViewPosition(frameBack, topW + gap, 0, topW, topH);
            }

            int vcw = 280;
            int botContentW = containerWidth - gap * 2 - vcw;
            int leftW = botContentW / 2;
            int rightW = botContentW - leftW;
            int botY = topH + gap;

            if (frameLeft != null) {
                frameLeft.setVisibility(View.VISIBLE);
                setViewPosition(frameLeft, 0, botY, leftW, botH);
            }
            if (frameVehicleControl != null) {
                frameVehicleControl.setVisibility(View.VISIBLE);
                int vcH = Math.min(520, botH);
                setViewPosition(frameVehicleControl, leftW + gap, botY + (botH - vcH) / 2, vcw, vcH);
            }
            if (frameRight != null) {
                frameRight.setVisibility(View.VISIBLE);
                setViewPosition(frameRight, leftW + gap + vcw + gap, botY, rightW, botH);
            }
        }
    }
    
    /**
     * Настройки视图Позиция и 大小
     */
    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setViewPosition(View view, int x, int y, int width, int height) {
        if (view == null) return;
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(width, height);
        view.setLayoutParams(params);
        view.setTranslationX(x);
        view.setTranslationY(y);
    }

    /**
     * 显示所有视图
     */
    public void showAllViews() {
        showView(frameFront, "front");
        showView(frameBack, "back");
        showView(frameLeft, "left");
        showView(frameRight, "right");
        showView(frameVehicleControl, "vehicle");
        showView(buttonContainer, "buttons");
    }
    
    /**
     * 显示单 шт.视图
     */
    private void showView(View view, String id) {
        if (view == null) return;
        view.setAlpha(1f);
        view.setClickable(true);
        view.setFocusable(true);
        layoutData.setVisible(id, true);
    }

    /**
     * ВосстановлениеСохранить 布局
     * @return  否有Сохранить 布局数据
     */
    private boolean restoreLayout() {
        if (appConfig.getCustomLayoutVersion() < LAYOUT_VERSION) {
            AppLog.d(TAG, "Версия layout устарела (" + appConfig.getCustomLayoutVersion() + " < " + LAYOUT_VERSION + "), очищаем старые данные");
            appConfig.clearCustomLayoutData();
            appConfig.setCustomLayoutVersion(LAYOUT_VERSION);
            return false;
        }
        String savedData = appConfig.getCustomLayoutData();
        boolean hasData = savedData != null && !savedData.isEmpty();
        
        if (hasData) {
            layoutData = LayoutData.fromJson(savedData);
            
            // проверка 否有完整 尺寸数据（至少有一 шт.Камера有尺寸数据)
            boolean hasCompleteData = layoutData.getWidth("front") > 0 || 
                                      layoutData.getWidth("back") > 0 ||
                                      layoutData.getWidth("left") > 0 ||
                                      layoutData.getWidth("right") > 0;
            
            if (hasCompleteData) {
                // 先Настройки容器透明（保持布局但不可见)，避免Восстановление过程 闪烁
                containerCameras.setAlpha(0f);
                
                //  от 布局数据Восстановление裁剪конфигурация до  AppConfig
                restoreCropDataFromLayout();
                
                // ожидание容器布局завершение后直接ВосстановлениеСохранить 布局
                containerCameras.post(() -> {
                    // 直接ВосстановлениеСохранить Позиция、尺寸 и ДругоеСтатус
                    restoreViewState(frameFront, "front");
                    restoreViewState(frameBack, "back");
                    restoreViewState(frameLeft, "left");
                    restoreViewState(frameRight, "right");
                    restoreViewState(frameVehicleControl, "vehicle");
                    restoreViewState(buttonContainer, "buttons");
                    
                    // 延迟Приложение裁剪并显示容器（ожидание TextureView 有действует尺寸)
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        applyAllCropsImmediately();
                        // 裁剪Приложение后显示容器
                        containerCameras.setAlpha(1f);
                        AppLog.d(TAG, "布局 и 裁剪Восстановлениезавершение");
                    }, 300);
                });
            } else {
                // 没有完整 尺寸数据，использованиеПо умолчанию布局
                AppLog.d(TAG, "Сохранить 布局数据不完整，использованиеПо умолчанию布局");
                return false;
            }
        }
        
        return hasData;
    }
    
    /**
     * Восстановление单 шт.视图 Статус
     */
    private void restoreViewState(View view, String id) {
        if (view == null) return;
        
        // Восстановление尺寸（Если 有Сохранить 尺寸)
        int savedWidth = layoutData.getWidth(id);
        int savedHeight = layoutData.getHeight(id);
        if (savedWidth > 0 && savedHeight > 0) {
            android.widget.FrameLayout.LayoutParams params = 
                    new android.widget.FrameLayout.LayoutParams(savedWidth, savedHeight);
            // 移除 gravity，использование绝 Позиция
            params.gravity = android.view.Gravity.NO_GRAVITY;
            view.setLayoutParams(params);
            AppLog.d(TAG, id + " Восстановление尺寸: " + savedWidth + "x" + savedHeight);
        } else if (view.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            // т.е.使没有Сохранить尺寸，такженеобходимо移除 gravity 以поддержкаПозицияВосстановление
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            if (params.gravity != android.view.Gravity.NO_GRAVITY) {
                params.gravity = android.view.Gravity.NO_GRAVITY;
                view.setLayoutParams(params);
            }
        }
        
        // ВосстановлениеПозиция
        float x = layoutData.getX(id);
        float y = layoutData.getY(id);
        // 只要有Сохранить Позиция数据Восстановление（包括 0,0 Позиция)
        // Используем translationX/Y чтобы избежать гонки с асинхронным layout pass от setLayoutParams выше
        if (layoutData.getWidth(id) > 0) {  // 有Сохранить过数据
            view.setTranslationX(x);
            view.setTranslationY(y);
            AppLog.d(TAG, id + " ВосстановлениеПозиция: (" + x + ", " + y + ")");
        }
        
        // Восстановление缩放（始终Приложение，包括По умолчанию值1.0，以确保控制按钮补偿正确)
        float scale = layoutData.getScale(id);
        applyScale(view, scale);
        
        // Восстановление可见性
        boolean visible = layoutData.isVisible(id);
        if (visible) {
            view.setAlpha(1f);
            view.setClickable(true);
            view.setFocusable(true);
        } else {
            view.setAlpha(0f);
            view.setClickable(false);
            view.setFocusable(false);
        }
    }

    /**
     * загрузка布局数据
     */
    private void loadLayoutData() {
        String json = appConfig.getCustomLayoutData();
        if (json != null && !json.isEmpty()) {
            layoutData = LayoutData.fromJson(json);
        }
    }

    /**
     * обновлениеКамера 宽Высокий比（根据实际Разрешение и Поворот 角度)
     * @param position Позиция（front/back/left/right)
     * @param width 原始宽度
     * @param height 原始Высокий度
     * @param rotation Поворот 角度
     */
    public void updateCameraAspectRatio(String position, int width, int height, int rotation) {
        int[] displayRatio = AppConfig.calculateDisplayRatio(width, height, rotation);
        layoutData.setAspectRatio(position, displayRatio[0], displayRatio[1]);
        AppLog.d(TAG, position + " 宽Высокий比: " + displayRatio[0] + ":" + displayRatio[1] + 
                " (Поворот " + rotation + "°)");
    }
    
    /**
     * ПолучениеКамера 显示宽Высокий比
     */
    public float getDisplayAspectRatio(String position) {
        return layoutData.getAspectRatio(position);
    }
    
    /**
     *  否处于编辑режим
     */
    public boolean isEditModeEnabled() {
        return editModeEnabled;
    }

    /**
     * 拖动触摸监听器
     * 拖动时平滑移动，松手时网格吸附
     */
    private class DragTouchListener implements View.OnTouchListener {
        private final View targetView;
        private final String viewId;
        private float startX, startY;      // 触摸Вкл始时视图 Позиция
        private float startRawX, startRawY; // 触摸Вкл始时手指 屏幕Позиция
        private boolean isDragging = false;
        private static final float TOUCH_SLOP = 10f;  // 触发拖动 минимум移动距离

        public DragTouchListener(View targetView, String viewId) {
            this.targetView = targetView;
            this.viewId = viewId;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // 只有 编辑режим才处理拖动
            if (!editModeEnabled) {
                return false;
            }
            
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // 直接记录视图Текущий  X/Y Позиция и 手指Позиция
                    startX = targetView.getTranslationX();
                    startY = targetView.getTranslationY();
                    startRawX = event.getRawX();
                    startRawY = event.getRawY();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    // 计算手指移动 距离
                    float deltaX = event.getRawX() - startRawX;
                    float deltaY = event.getRawY() - startRawY;
                    
                    // проверка 否超过触发阈值（避免误触)
                    if (!isDragging) {
                        if (Math.abs(deltaX) > TOUCH_SLOP || Math.abs(deltaY) > TOUCH_SLOP) {
                            isDragging = true;
                            // 将起始Позиция吸附 до 网格，确保移动基于网格 齐 Позиция
                            startX = snapToGrid(startX);
                            startY = snapToGrid(startY);
                        } else {
                            return true;  // 还Не Вкл始拖动，ожидание
                        }
                    }
                    
                    // 计算新Позиция = 初始Позиция + 移动距离，然后吸附 до 网格
                    float newX = snapToGrid(startX + deltaX);
                    float newY = snapToGrid(startY + deltaY);
                    
                    // Приложение边界限制
                    float[] bounded = applyBoundaryLimits(newX, newY);
                    
                    // НастройкиПозиция（每 раз移动все吸附 до  20dp 网格，方便 齐)
                    targetView.setTranslationX(bounded[0]);
                    targetView.setTranslationY(bounded[1]);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        // 松手时进行网格吸附
                        float finalX = snapToGrid(targetView.getTranslationX());
                        float finalY = snapToGrid(targetView.getTranslationY());
                        
                        // 边缘吸附并确保 边界内
                        float[] snapped = applyEdgeSnapping(finalX, finalY);
                        
                        targetView.setTranslationX(snapped[0]);
                        targetView.setTranslationY(snapped[1]);
                        
                        // обновление布局数据
                        layoutData.setPosition(viewId, snapped[0], snapped[1]);
                        
                        // Сохранить до  应режим  appConfig 参数
                        savePositionToCurrentMode(viewId, (int) snapped[0], (int) snapped[1], 
                                targetView.getWidth(), targetView.getHeight());
                        
                        AppLog.d(TAG, viewId + " 移动 до  (" + (int) snapped[0] + ", " + (int) snapped[1] + ")");
                    }
                    return true;

                default:
                    return false;
            }
        }
        
        /**
         * Приложение边界限制（Отключено)
         * разрешить视图自由移动 до 任意Позиция，包括边界外
         */
        private float[] applyBoundaryLimits(float x, float y) {
            // 不做任何边界限制，直接返回原始坐标
            return new float[]{x, y};
        }
        
        /**
         * 边缘吸附处理（Отключено边界限制)
         * 只保留网格 齐функция，разрешить自由移动 до 边界外
         */
        private float[] applyEdgeSnapping(float x, float y) {
            // 只做网格吸附，不做边界限制
            // 坐标经  ACTION_MOVE 吸附过网格，这里直接返回
            return new float[]{x, y};
        }
        
        /**
         * 吸附 до 网格
         */
        private float snapToGrid(float value) {
            return Math.round(value / GRID_SIZE) * GRID_SIZE;
        }
    }

    /**
     * СохранитьПозиция до Текущийрежим  appConfig 参数
     * 根据Текущий选 режим（前轮/后轮/普通)Сохранить до  应 参数
     */
    private void savePositionToCurrentMode(String viewId, int x, int y, int width, int height) {
        // 判断Текущийрежим
        Button btnFrontWheel = null;
        Button btnRearWheel = null;
        if (frameVehicleControl != null) {
            btnFrontWheel = frameVehicleControl.findViewById(R.id.btn_front_wheel);
            btnRearWheel = frameVehicleControl.findViewById(R.id.btn_rear_wheel);
        }

        boolean isFrontWheelMode = btnFrontWheel != null && btnFrontWheel.getTag() != null && (Boolean) btnFrontWheel.getTag();
        boolean isRearWheelMode = btnRearWheel != null && btnRearWheel.getTag() != null && (Boolean) btnRearWheel.getTag();

        // 只Сохранить左视图 и 右视图 Позиция
        if ("left".equals(viewId)) {
            if (isFrontWheelMode) {
                appConfig.setFrontWheelLeftViewParams(width, height, x, y, appConfig.getFrontWheelLeftRotation(0));
                AppLog.d(TAG, "前轮режим左视图参数Сохранить");
            } else if (isRearWheelMode) {
                appConfig.setRearWheelLeftViewParams(width, height, x, y, appConfig.getRearWheelLeftRotation(0));
                AppLog.d(TAG, "后轮режим左视图参数Сохранить");
            } else {
                appConfig.setNormalLeftViewParams(width, height, x, y, appConfig.getNormalLeftRotation(0));
                AppLog.d(TAG, "普通режим左视图参数Сохранить");
            }
        } else if ("right".equals(viewId)) {
            if (isFrontWheelMode) {
                appConfig.setFrontWheelRightViewParams(width, height, x, y, appConfig.getFrontWheelRightRotation(0));
                AppLog.d(TAG, "前轮режим右视图参数Сохранить");
            } else if (isRearWheelMode) {
                appConfig.setRearWheelRightViewParams(width, height, x, y, appConfig.getRearWheelRightRotation(0));
                AppLog.d(TAG, "后轮режим右视图参数Сохранить");
            } else {
                appConfig.setNormalRightViewParams(width, height, x, y, appConfig.getNormalRightRotation(0));
                AppLog.d(TAG, "普通режим右视图参数Сохранить");
            }
        }
    }

    /**
     * 布局数据类
     */
    public static class LayoutData {
        private JSONObject data;

        public LayoutData() {
            data = new JSONObject();
        }

        public static LayoutData fromJson(String json) {
            LayoutData layoutData = new LayoutData();
            try {
                layoutData.data = new JSONObject(json);
            } catch (JSONException e) {
                AppLog.e(TAG, "解析布局数据Ошибка", e);
            }
            return layoutData;
        }

        public String toJson() {
            return data.toString();
        }

        private JSONObject getOrCreateObject(String key) {
            try {
                if (!data.has(key)) {
                    data.put(key, new JSONObject());
                }
                return data.getJSONObject(key);
            } catch (JSONException e) {
                return new JSONObject();
            }
        }

        public void setPosition(String id, float x, float y) {
            try {
                JSONObject obj = getOrCreateObject(id);
                obj.put("x", x);
                obj.put("y", y);
            } catch (JSONException e) {
                AppLog.e(TAG, "СохранитьПозицияОшибка", e);
            }
        }

        public float getX(String id) {
            try {
                if (data.has(id)) {
                    return (float) data.getJSONObject(id).optDouble("x", 0);
                }
            } catch (JSONException e) {
                // ignore
            }
            return 0;
        }

        public float getY(String id) {
            try {
                if (data.has(id)) {
                    return (float) data.getJSONObject(id).optDouble("y", 0);
                }
            } catch (JSONException e) {
                // ignore
            }
            return 0;
        }

        public void setScale(String id, float scale) {
            try {
                JSONObject obj = getOrCreateObject(id);
                obj.put("scale", scale);
            } catch (JSONException e) {
                AppLog.e(TAG, "Сохранить缩放Ошибка", e);
            }
        }

        public float getScale(String id) {
            try {
                if (data.has(id)) {
                    return (float) data.getJSONObject(id).optDouble("scale", 1.0);
                }
            } catch (JSONException e) {
                // ignore
            }
            return 1.0f;
        }

        public void setVisible(String id, boolean visible) {
            try {
                JSONObject obj = getOrCreateObject(id);
                obj.put("visible", visible);
            } catch (JSONException e) {
                AppLog.e(TAG, "Сохранить可见性Ошибка", e);
            }
        }

        public boolean isVisible(String id) {
            try {
                if (data.has(id)) {
                    return data.getJSONObject(id).optBoolean("visible", true);
                }
            } catch (JSONException e) {
                // ignore
            }
            return true;
        }

        public void setAspectRatio(String id, int width, int height) {
            try {
                JSONObject obj = getOrCreateObject(id);
                obj.put("ratioWidth", width);
                obj.put("ratioHeight", height);
            } catch (JSONException e) {
                AppLog.e(TAG, "Сохранить宽Высокий比Ошибка", e);
            }
        }

        public float getAspectRatio(String id) {
            try {
                if (data.has(id)) {
                    JSONObject obj = data.getJSONObject(id);
                    int w = obj.optInt("ratioWidth", 16);
                    int h = obj.optInt("ratioHeight", 9);
                    return (float) w / h;
                }
            } catch (JSONException e) {
                // ignore
            }
            return 16f / 9f;  // По умолчанию16:9
        }

        /**
         * Сохранить视图 宽Высокий
         */
        public void setSize(String id, int width, int height) {
            try {
                JSONObject obj = getOrCreateObject(id);
                obj.put("width", width);
                obj.put("height", height);
            } catch (JSONException e) {
                AppLog.e(TAG, "Сохранить尺寸Ошибка", e);
            }
        }

        /**
         * ПолучениеСохранить 宽度
         * @return 宽度，-1 表示Не Настройки
         */
        public int getWidth(String id) {
            try {
                if (data.has(id)) {
                    return data.getJSONObject(id).optInt("width", -1);
                }
            } catch (JSONException e) {
                // ignore
            }
            return -1;
        }

        /**
         * ПолучениеСохранить Высокий度
         * @return Высокий度，-1 表示Не Настройки
         */
        public int getHeight(String id) {
            try {
                if (data.has(id)) {
                    return data.getJSONObject(id).optInt("height", -1);
                }
            } catch (JSONException e) {
                // ignore
            }
            return -1;
        }
        
        /**
         * Сохранить裁剪数据
         * @param id Камера标识（front/back/left/right)
         * @param top 裁剪像素
         * @param bottom 裁剪像素
         * @param left 左裁剪像素
         * @param right 右裁剪像素
         */
        public void setCrop(String id, int top, int bottom, int left, int right) {
            try {
                JSONObject obj = getOrCreateObject(id);
                JSONObject cropObj = new JSONObject();
                cropObj.put("top", top);
                cropObj.put("bottom", bottom);
                cropObj.put("left", left);
                cropObj.put("right", right);
                obj.put("crop", cropObj);
            } catch (JSONException e) {
                AppLog.e(TAG, "Сохранить裁剪数据Ошибка", e);
            }
        }
        
        /**
         * Получение裁剪数据
         * @param id Камера标识
         * @param direction 方 к （top/bottom/left/right)
         * @return 裁剪像素值，-1 表示Не Настройки
         */
        public int getCrop(String id, String direction) {
            try {
                if (data.has(id)) {
                    JSONObject obj = data.getJSONObject(id);
                    if (obj.has("crop")) {
                        return obj.getJSONObject("crop").optInt(direction, -1);
                    }
                }
            } catch (JSONException e) {
                // ignore
            }
            return -1;
        }
        
        /**
         * проверка 否有裁剪数据
         */
        public boolean hasCrop(String id) {
            try {
                if (data.has(id)) {
                    return data.getJSONObject(id).has("crop");
                }
            } catch (JSONException e) {
                // ignore
            }
            return false;
        }
    }
}
