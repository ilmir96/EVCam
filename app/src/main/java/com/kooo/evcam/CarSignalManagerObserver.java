package com.kooo.evcam;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.lang.reflect.Method;

/**
 * CarSignalManager 转 к 灯观察者（基于吉利L6/L7真实API)
 * 
 * 核心方法：getIndcrSts()
 * 返回值：0=Закрыто, 1=左转, 2=右转, 3=双闪
 * 
 * инициализация方式：
 * 1. ECARX API: ecarxcar_service → ECarXCar.createCar() → getCarManager("car_signal")
 * 2. CarSensor API: CarSensor.create() (备用)
 */
public class CarSignalManagerObserver {
    
    private static final String TAG = "CarSignalManagerObserver";
    private static final long POLL_INTERVAL_MS = 200; // 200ms轮询一 раз
    private static final long INIT_RETRY_DELAY_MS = 5000; // инициализацияОшибка重试间隔
    private static final int MAX_INIT_RETRIES = 3;
    
    /**
     * 转 к 灯信号回调接口
     */
    public interface TurnSignalListener {
        /** 转 к 灯Статус变化 */
        void onTurnSignal(String direction, boolean on);
        /** ПодключениеСтатус变化 */
        void onConnectionStateChanged(boolean connected);
    }
    
    private final Context context;
    private final TurnSignalListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private Object carSignalManager = null;
    private Method getIndcrStsMethod = null;  // Получение转 к 灯Статус 方法
    
    private volatile boolean running = false;
    private volatile boolean connected = false;
    
    // 一 раз 转 к 灯Статус（0=Закрыто, 1=左转, 2=右转, 3=双闪)
    private int lastTurnSignalState = 0;
    
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            
            try {
                pollTurnSignalState();
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to poll turn signal state", e);
            } finally {
                if (running) {
                    handler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        }
    };
    
    public CarSignalManagerObserver(Context context, TurnSignalListener listener) {
        this.context = context;
        this.listener = listener;
    }
    
    /**
     * Запуск监听
     */
    public void start() {
        if (running) return;
        running = true;
        lastTurnSignalState = -1;
        attemptInit(0);
    }

    private void attemptInit(int attempt) {
        new Thread(() -> {
            boolean success = initCarSignalManager();

            if (listener != null) {
                handler.post(() -> listener.onConnectionStateChanged(success));
            }

            if (success) {
                handler.post(pollRunnable);
            } else if (running && attempt < MAX_INIT_RETRIES) {
                AppLog.w(TAG, "Init failed, retry " + (attempt + 1) + "/" + MAX_INIT_RETRIES
                        + " in " + INIT_RETRY_DELAY_MS + "ms");
                handler.postDelayed(() -> {
                    if (running) attemptInit(attempt + 1);
                }, INIT_RETRY_DELAY_MS);
            } else if (running) {
                AppLog.e(TAG, "Init failed after " + MAX_INIT_RETRIES + " retries, observer inactive");
            }
        }).start();
    }
    
    /**
     * Остановка监听
     */
    public void stop() {
        running = false;
        connected = false;
        handler.removeCallbacks(pollRunnable);
        carSignalManager = null;
        getIndcrStsMethod = null;
    }
    
    /**
     * Текущий 否Подключено
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 观察者 否存活（инициализация且轮询)
     */
    public boolean isAlive() {
        return running && connected;
    }
    
    /**
     * 一 раз性Подключениетестирование（用于 UI Статуспроверка)
     */
    public static boolean testConnection(Context context) {
        try {
            // 方法1：попытка ECARX API
            try {
                Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
                Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
                Object binder = getServiceMethod.invoke(null, "ecarxcar_service");
                
                if (binder != null) {
                    Class<?> stubClass = Class.forName("ecarx.car.IECarXCar$Stub");
                    Method asInterfaceMethod = stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"));
                    Object eCarXCar = asInterfaceMethod.invoke(null, binder);
                    
                    if (eCarXCar != null) {
                        Class<?> eCarXCarClass = Class.forName("ecarx.car.ECarXCar");
                        Class<?> iECarXCarClass = Class.forName("ecarx.car.IECarXCar");
                        Method createCarMethod = eCarXCarClass.getMethod("createCar", Context.class, iECarXCarClass);
                        Object car = createCarMethod.invoke(null, context, eCarXCar);
                        
                        if (car != null) {
                            Method getCarManagerMethod = car.getClass().getMethod("getCarManager", String.class, iECarXCarClass);
                            Object carSignalManager = getCarManagerMethod.invoke(car, "car_signal", eCarXCar);
                            
                            if (carSignalManager != null) {
                                Method method = carSignalManager.getClass().getMethod("getIndcrSts");
                                Object result = method.invoke(carSignalManager);
                                AppLog.d(TAG, "✅ ECARX CarSignalManager Доступно，Текущий转 к 灯Статус: " + result);
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AppLog.d(TAG, "ECARX API 不Доступно: " + e.getMessage());
            }
            
            // 方法2：попытка CarSensor API (备用)
            try {
                Class<?> clazz = Class.forName("com.ecarx.xui.adaptapi.car.sensor.CarSensor");
                Method createMethod = clazz.getMethod("create", Context.class);
                Object carSensor = createMethod.invoke(null, context);
                
                if (carSensor != null) {
                    Method method = carSensor.getClass().getMethod("getIndcrSts");
                    Object result = method.invoke(carSensor);
                    AppLog.d(TAG, "✅ CarSensor API Доступно，Текущий转 к 灯Статус: " + result);
                    return true;
                }
            } catch (Exception e) {
                AppLog.d(TAG, "CarSensor API 不Доступно: " + e.getMessage());
            }
            
            AppLog.e(TAG, "❌ 所有 Car API 均不Доступно");
            return false;
        } catch (Exception e) {
            AppLog.e(TAG, "CarSignalManager test failed: " + e.getMessage());
            return false;
        }
    }
    
    // ==================== Internal ====================
    
    /**
     * инициализация CarSignalManager（参考 L7Test目 Успешно实现)
     */
    private boolean initCarSignalManager() {
        try {
            AppLog.d(TAG, "🔍 Вкл始инициализация CarSignalManager...");
            
            // 方法1：попытка通过 ServiceManager Получение ecarxcar_service
            try {
                Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
                Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
                Object binder = getServiceMethod.invoke(null, "ecarxcar_service");
                
                if (binder != null) {
                    AppLog.d(TAG, "✅ ecarxcar_service BinderПолучениеУспешно");
                    Class<?> stubClass = Class.forName("ecarx.car.IECarXCar$Stub");
                    Method asInterfaceMethod = stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"));
                    Object eCarXCar = asInterfaceMethod.invoke(null, binder);
                    
                    if (eCarXCar != null) {
                        Class<?> eCarXCarClass = Class.forName("ecarx.car.ECarXCar");
                        Class<?> iECarXCarClass = Class.forName("ecarx.car.IECarXCar");
                        Method createCarMethod = eCarXCarClass.getMethod("createCar", Context.class, iECarXCarClass);
                        Object car = createCarMethod.invoke(null, context, eCarXCar);
                        
                        if (car != null) {
                            Method getCarManagerMethod = car.getClass().getMethod("getCarManager", String.class, iECarXCarClass);
                            carSignalManager = getCarManagerMethod.invoke(car, "car_signal", eCarXCar);
                            
                            if (carSignalManager != null) {
                                AppLog.d(TAG, "✅ ECARX CarSignalManager инициализацияУспешно");
                                getIndcrStsMethod = carSignalManager.getClass().getMethod("getIndcrSts");
                                
                                // тестирование调用
                                Object testResult = getIndcrStsMethod.invoke(carSignalManager);
                                AppLog.d(TAG, "📊 Текущий转 к 灯Статус: " + testResult);
                                
                                connected = true;
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AppLog.w(TAG, "ECARX API инициализацияОшибка: " + e.getMessage());
            }
            
            // 方法2：попытка CarSensor API (备用)
            try {
                AppLog.d(TAG, "попытка备用 CarSensor API...");
                Class<?> clazz = Class.forName("com.ecarx.xui.adaptapi.car.sensor.CarSensor");
                Method createMethod = clazz.getMethod("create", Context.class);
                carSignalManager = createMethod.invoke(null, context);
                
                if (carSignalManager != null) {
                    AppLog.d(TAG, "✅ CarSensor инициализацияУспешно(备用API)");
                    getIndcrStsMethod = carSignalManager.getClass().getMethod("getIndcrSts");
                    
                    // тестирование调用
                    Object testResult = getIndcrStsMethod.invoke(carSignalManager);
                    AppLog.d(TAG, "📊 Текущий转 к 灯Статус: " + testResult);
                    
                    connected = true;
                    return true;
                }
            } catch (Exception e) {
                AppLog.w(TAG, "CarSensor API инициализацияОшибка: " + e.getMessage());
            }
            
            AppLog.e(TAG, "❌ 所有 Car API инициализацияОшибка");
            return false;
            
        } catch (Exception e) {
            AppLog.e(TAG, "❌ CarSignalManager инициализацияаномалия", e);
            carSignalManager = null;
            getIndcrStsMethod = null;
            connected = false;
            return false;
        }
    }
    
    /**
     * 轮询转 к 灯Статус（200ms间隔)
     */
    private void pollTurnSignalState() {
        if (carSignalManager == null || getIndcrStsMethod == null) {
            return;
        }
        
        try {
            // 调用 getIndcrSts() Получение转 к 灯Статус
            // 返回值：0=Закрыто, 1=左转, 2=右转, 3=双闪
            Object result = getIndcrStsMethod.invoke(carSignalManager);
            
            if (result != null) {
                int currentState = Integer.parseInt(result.toString());
                checkTurnSignalChange(currentState);
            } else {
                AppLog.w(TAG, "⚠️ getIndcrSts() Возвращает null");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "❌ 转 к 灯СтатусОшибка чтения: " + e.getMessage());
        }
    }
    
    /**
     * 检测转 к 灯Статус变化并Уведомление监听器
     * @param currentState ТекущийСтатус: 0=Закрыто, 1=左转, 2=右转, 3=双闪
     */
    private void checkTurnSignalChange(int currentState) {
        if (lastTurnSignalState != currentState) {
            String statusDesc = getTurnSignalDesc(currentState);
            AppLog.d(TAG, "🔄 转 к 灯Статус变化: " + lastTurnSignalState + " → " + currentState + " (" + statusDesc + ")");
            
            // Уведомление监听器
            if (listener != null) {
                // 根据Статус转换为方 к  и ВклВыклИнформация
                switch (currentState) {
                    case 0: // Закрыто
                        // 只  от 非ЗакрытоСтатус切换 до ЗакрытоСтатус时，才УведомлениеЗакрыто
                        // 避免重复触发 startHideTimer()
                        if (lastTurnSignalState == 1) {
                            //  от 左转切换 до Закрыто
                            handler.post(() -> listener.onTurnSignal("left", false));
                        } else if (lastTurnSignalState == 2) {
                            //  от 右转切换 до Закрыто
                            handler.post(() -> listener.onTurnSignal("right", false));
                        } else if (lastTurnSignalState == 3) {
                            //  от 双闪切换 до Закрыто
                            handler.post(() -> {
                                listener.onTurnSignal("left", false);
                                listener.onTurnSignal("right", false);
                            });
                        }
                        break;
                        
                    case 1: // 左转
                        handler.post(() -> listener.onTurnSignal("left", true));
                        break;
                        
                    case 2: // 右转
                        handler.post(() -> listener.onTurnSignal("right", true));
                        break;
                        
                    case 3: // 双闪
                        handler.post(() -> {
                            listener.onTurnSignal("left", true);
                            listener.onTurnSignal("right", true);
                        });
                        break;
                }
            }
            
            lastTurnSignalState = currentState;
        }
    }
    
    /**
     * Получение转 к 灯Статус描述
     */
    private String getTurnSignalDesc(int status) {
        switch (status) {
            case 0: return "Закрыть";
            case 1: return "Левый поворот";
            case 2: return "Правый поворот";
            case 3: return "Аварийка";
            default: return "Неизв.(" + status + ")";
        }
    }
}
