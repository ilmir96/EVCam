package com.kooo.evcam;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

/**
 * 车门信号观察者（基于吉利L6/L7 CarSignalManager API)
 * Author：AbuCoder
 * Date：2023/07/07 
 * Gitee:https://gitee.com/rahman/EVCam
 * Description：车门信号观察者，用于监听车门Статус变化，если门открытьилиЗакрыто。
 * 
 * 核心方法：
 * - getDoorDrvrSts() - 主驾驶门Статус
 * - getDoorPassSts() - 副驾驶门Статус  
 * - getDoorLeReSts() - 左后门Статус
 * - getDoorRiReSts() - 右后门Статус
 * 
 * 返回值：1=открыть, 2=Закрыто
 */
public class DoorSignalObserver {
    
    private static final String TAG = "DoorSignalObserver";
    private static final long POLL_INTERVAL_MS = 500; // 500ms轮询一 раз
    
    /**
     * 车门信号回调接口
     */
    public interface DoorSignalListener {
        /** 车门Статус变化 */
        void onDoorOpen(String side);
        void onDoorClose(String side);
        /** ПодключениеСтатус变化 */
        void onConnectionStateChanged(boolean connected);
    }
    
    private final Context context;
    private final DoorSignalListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private Object carSignalManager = null;
    private Method getDoorDrvrStsMethod = null;  // 主驾驶门
    private Method getDoorPassStsMethod = null;  // 副驾驶门
    private Method getDoorLeReStsMethod = null;  // 左后门
    private Method getDoorRiReStsMethod = null;  // 右后门
    
    private volatile boolean running = false;
    private volatile boolean connected = false;
    
    // 一 раз 车门Статус（1=открыть, 2=Закрыто)
    private int lastDoorDrvrSts = 2;
    private int lastDoorPassSts = 2;
    private int lastDoorLeReSts = 2;
    private int lastDoorRiReSts = 2;
    
    // 车门Вкл启标志（用于判断 否необходимоЗакрытоКамера)
    private boolean isPassDoorOpen = false;      // 副驾驶门
    private boolean isLeftRearDoorOpen = false;  // 左后门
    private boolean isRightRearDoorOpen = false; // 右后门
    
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            AppLog.d(TAG, "🚪 pollRunnable.run() выполнение，running=" + running);
            
            if (!running) {
                AppLog.w(TAG, "🚪 running=false，Остановка轮询");
                return;
            }
            
            try {
                pollDoorState();
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to poll door state", e);
            } finally {
                if (running) {
                    AppLog.d(TAG, "🚪 调度 раз轮询，延迟 " + POLL_INTERVAL_MS + "ms");
                    handler.postDelayed(this, POLL_INTERVAL_MS);
                } else {
                    AppLog.w(TAG, "🚪 running=false，不再调度 раз轮询");
                }
            }
        }
    };
    
    public DoorSignalObserver(Context context, DoorSignalListener listener) {
        this.context = context;
        this.listener = listener;
    }
    
    /**
     * Запуск监听
     */
    public void start() {
        if (running) {
            AppLog.w(TAG, "🚪 车门监听器经 Работа，跳过重复Запуск");
            return;
        }
        running = true;
        
        AppLog.i(TAG, "🚪 ========== DoorSignalObserver.start() Вкл始выполнение ==========");
        
        // СбросСтатус
        lastDoorDrvrSts = 2;
        lastDoorPassSts = 2;
        lastDoorLeReSts = 2;
        lastDoorRiReSts = 2;
        isPassDoorOpen = false;
        isLeftRearDoorOpen = false;
        isRightRearDoorOpen = false;
        
        AppLog.i(TAG, "🚪 Запускинициализация线程...");
        new Thread(() -> {
            AppLog.i(TAG, "🚪 инициализация线程Вкл始Работа");
            boolean success = initCarSignalManager();
            AppLog.i(TAG, "🚪 инициализация结果: " + (success ? "Успешно" : "Ошибка"));
            
            if (listener != null) {
                handler.post(() -> {
                    AppLog.i(TAG, "🚪 УведомлениеПодключениеСтатус变化: " + (success ? "Подключено" : "Не подключено"));
                    listener.onConnectionStateChanged(success);
                });
            }
            
            if (success) {
                AppLog.i(TAG, "🚪 准备Запуск轮询 Runnable...");
                // 延迟 100ms Запуск轮询，避免立т.е. Остановка
                handler.postDelayed(() -> {
                    AppLog.i(TAG, "🚪 ✅ 轮询 Runnable 准备выполнение，running=" + running + ", connected=" + connected);
                    if (running && connected) {
                        AppLog.i(TAG, "🚪 Вкл始Первый раз轮询");
                        pollRunnable.run();
                    } else {
                        AppLog.e(TAG, "🚪 ❌ running=" + running + ", connected=" + connected + "，轮询Не Запуск");
                    }
                }, 100);
            } else {
                AppLog.e(TAG, "🚪 ❌ инициализацияОшибка，轮询Не Запуск");
            }
        }).start();
        
        AppLog.i(TAG, "🚪 ========== DoorSignalObserver.start() выполнениезавершение ==========");
    }
    
    /**
     * Остановка监听
     */
    public void stop() {
        AppLog.i(TAG, "🚪 ========== DoorSignalObserver.stop() Вкл始выполнение ==========");
        AppLog.i(TAG, "🚪 Текущий running=" + running);
        
        running = false;
        connected = false;
        
        // 移除所有待выполнение  Runnable
        handler.removeCallbacks(pollRunnable);
        AppLog.i(TAG, "🚪 移除所有待выполнение 轮询 Runnable");
        
        carSignalManager = null;
        getDoorDrvrStsMethod = null;
        getDoorPassStsMethod = null;
        getDoorLeReStsMethod = null;
        getDoorRiReStsMethod = null;
        
        AppLog.i(TAG, "🚪 ========== DoorSignalObserver.stop() выполнениезавершение ==========");
    }
    
    /**
     * Текущий 否Подключено
     */
    public boolean isConnected() {
        return connected;
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
                                Method method = carSignalManager.getClass().getMethod("getDoorDrvrSts");
                                Object result = method.invoke(carSignalManager);
                                AppLog.d(TAG, "✅ ECARX CarSignalManager Доступно，主驾门Статус: " + result);
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
                    Method method = carSensor.getClass().getMethod("getDoorDrvrSts");
                    Object result = method.invoke(carSensor);
                    AppLog.d(TAG, "✅ CarSensor API Доступно，主驾门Статус: " + result);
                    return true;
                }
            } catch (Exception e) {
                AppLog.d(TAG, "CarSensor API 不Доступно: " + e.getMessage());
            }
            
            AppLog.e(TAG, "❌ 所有 Car API 均不Доступно");
            return false;
        } catch (Exception e) {
            AppLog.e(TAG, "DoorSignalObserver test failed: " + e.getMessage());
            return false;
        }
    }
    
    // ==================== Internal ====================
    
    /**
     * инициализация CarSignalManager
     */
    private boolean initCarSignalManager() {
        try {
            AppLog.d(TAG, "🔍 Вкл始инициализация CarSignalManager (车门监听)...");
            
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
                                // Получение车门Статус方法
                                getDoorDrvrStsMethod = carSignalManager.getClass().getMethod("getDoorDrvrSts");
                                getDoorPassStsMethod = carSignalManager.getClass().getMethod("getDoorPassSts");
                                getDoorLeReStsMethod = carSignalManager.getClass().getMethod("getDoorLeReSts");
                                getDoorRiReStsMethod = carSignalManager.getClass().getMethod("getDoorRiReSts");
                                
                                // тестирование调用
                                Object testResult = getDoorDrvrStsMethod.invoke(carSignalManager);
                                AppLog.d(TAG, "📊 Текущий主驾门Статус: " + testResult);
                                
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
                    // Получение车门Статус方法
                    getDoorDrvrStsMethod = carSignalManager.getClass().getMethod("getDoorDrvrSts");
                    getDoorPassStsMethod = carSignalManager.getClass().getMethod("getDoorPassSts");
                    getDoorLeReStsMethod = carSignalManager.getClass().getMethod("getDoorLeReSts");
                    getDoorRiReStsMethod = carSignalManager.getClass().getMethod("getDoorRiReSts");
                    
                    // тестирование调用
                    Object testResult = getDoorDrvrStsMethod.invoke(carSignalManager);
                    AppLog.d(TAG, "📊 Текущий主驾门Статус: " + testResult);
                    
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
            connected = false;
            return false;
        }
    }
    
    /**
     * 轮询车门Статус（500ms间隔)
     */
    private void pollDoorState() {
        if (carSignalManager == null) {
            AppLog.w(TAG, "🚪 carSignalManager 为 null，跳过轮询");
            return;
        }
        
        try {
            // Получение四 шт.车门Статус
            int drvr = Integer.parseInt(getDoorDrvrStsMethod.invoke(carSignalManager).toString());
            int pass = Integer.parseInt(getDoorPassStsMethod.invoke(carSignalManager).toString());
            int leRe = Integer.parseInt(getDoorLeReStsMethod.invoke(carSignalManager).toString());
            int riRe = Integer.parseInt(getDoorRiReStsMethod.invoke(carSignalManager).toString());
            
            // 🔍 每 развсе输出Текущий车门Статус（用于отладка)
            AppLog.d(TAG, String.format("🚪 车门Статус - 主驾:%d 副驾:%d ЛЗ:%d ПрЗ:%d", drvr, pass, leRe, riRe));
            
            // 主驾驶门（不触发Камера，只记录Статус)
            if (drvr != lastDoorDrvrSts) {
                AppLog.i(TAG, "🚪 主驾门Статус变化: " + lastDoorDrvrSts + " → " + drvr);
                lastDoorDrvrSts = drvr;
            }
            
            // 副驾驶门（右侧Камера)
            checkDoorChange("Дверь пассажира", pass, lastDoorPassSts, (opened) -> {
                isPassDoorOpen = opened;
                if (opened) {
                    notifyDoorOpen("right");
                } else {
                    // 只有当副驾门 и 右后门всеЗакрыто时才Закрыто右侧Камера
                    if (!isRightRearDoorOpen) {
                        notifyDoorClose("right");
                    }
                }
            });
            lastDoorPassSts = pass;
            
            // 左后门（左侧Камера)
            checkDoorChange("Левая задняя дверь", leRe, lastDoorLeReSts, (opened) -> {
                isLeftRearDoorOpen = opened;
                if (opened) {
                    notifyDoorOpen("left");
                } else {
                    // 左后门Закрыто可以Закрыто左侧Камера
                    notifyDoorClose("left");
                }
            });
            lastDoorLeReSts = leRe;
            
            // 右后门（右侧Камера)
            checkDoorChange("Правая задняя дверь", riRe, lastDoorRiReSts, (opened) -> {
                isRightRearDoorOpen = opened;
                if (opened) {
                    notifyDoorOpen("right");
                } else {
                    // 只有当副驾门 и 右后门всеЗакрыто时才Закрыто右侧Камера
                    if (!isPassDoorOpen) {
                        notifyDoorClose("right");
                    }
                }
            });
            lastDoorRiReSts = riRe;
            
        } catch (Exception e) {
            AppLog.e(TAG, "❌ 车门СтатусОшибка чтения: " + e.getMessage());
        }
    }
    
    /**
     * проверка车门Статус变化
     */
    private void checkDoorChange(String doorName, int currentState, int lastState, DoorChangeCallback callback) {
        if (currentState != lastState) {
            String stateDesc = (currentState == 1) ? "Открыта" : "Закрыть";
            AppLog.i(TAG, "🚪 " + doorName + "Статус变化: " + lastState + " → " + currentState + " (" + stateDesc + ")");
            
            if (currentState == 1 && lastState != 1) {
                // 车门открыть
                AppLog.i(TAG, "🚪🚪🚪 触发车门открыть回调: " + doorName);
                callback.onChange(true);
            } else if (currentState == 2 && lastState == 1) {
                // 车门Закрыто
                AppLog.i(TAG, "🚪🚪🚪 触发车门Закрыто回调: " + doorName);
                callback.onChange(false);
            }
        }
    }
    
    /**
     * Уведомление车门открыть
     */
    private void notifyDoorOpen(String side) {
        if (listener != null) {
            handler.post(() -> listener.onDoorOpen(side));
        }
    }
    
    /**
     * Уведомление车门Закрыто
     */
    private void notifyDoorClose(String side) {
        if (listener != null) {
            handler.post(() -> listener.onDoorClose(side));
        }
    }
    
    /**
     * 车门变化回调接口
     */
    private interface DoorChangeCallback {
        void onChange(boolean opened);
    }
}
