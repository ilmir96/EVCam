package com.kooo.evcam;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ADB Разрешениепредоставить助手
 * 通过 ADB TCP 协议Подключение localhost:5555，автоматическипредоставить所有необходимо Разрешение。
 *
 * 工作原理：
 * 1. 通过 TCP Подключение до 设备本地  ADB 守护进程 (端口 5555)
 * 2. использование ADB 协议进行握手（поддержка认证)
 * 3. 以 shell 用户身份выполнение pm grant / appops / settings 等команда
 * 4. 通过回调报告выполнение进度 и 结果
 */
public class AdbPermissionHelper {
    private static final String TAG = "AdbPermissionHelper";

    // ==================== ADB 协议常量 ====================
    private static final int A_CNXN = 0x4e584e43; // CNXN
    private static final int A_AUTH = 0x48545541; // AUTH
    private static final int A_OPEN = 0x4e45504f; // OPEN
    private static final int A_OKAY = 0x59414b4f; // OKAY
    private static final int A_CLSE = 0x45534c43; // CLSE
    private static final int A_WRTE = 0x45545257; // WRTE

    private static final int ADB_AUTH_TOKEN = 1;
    private static final int ADB_AUTH_SIGNATURE = 2;
    private static final int ADB_AUTH_RSAPUBLICKEY = 3;

    private static final int A_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 4096;

    // Подключение参数
    private static final String ADB_HOST = "127.0.0.1";
    private static final int ADB_PORT = 5555;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int AUTH_ACCEPT_TIMEOUT_MS = 30000;
    private static final int INSTALL_TIMEOUT_MS = 120000; // pm install 最多等 2  мин.

    // RSA 密钥参数
    private static final int RSA_KEY_BITS = 2048;
    private static final String PRIVATE_KEY_FILE = "adb_private_key";
    private static final String PUBLIC_KEY_FILE = "adb_public_key";

    // SHA-1 AlgorithmIdentifier DigestInfo 前缀 (PKCS#1 v1.5)
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    // ==================== 实例Статус ====================
    private final Context context;
    private final String packageName;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private KeyPair keyPair;

    // ПодключениеСтатус
    private Socket socket;
    private InputStream socketIn;
    private OutputStream socketOut;
    private int serverMaxData = MAX_PAYLOAD;
    private int localIdCounter = 1;

    // выполнение计数
    private int successCount = 0;
    private int failCount = 0;

    private volatile boolean cancelled = false;

    // ==================== 回调接口 ====================
    public interface Callback {
        /**  д.志输出（ 主线程调用) */
        void onLog(String message);

        /** выполнениезавершение（ 主线程调用) */
        void onComplete(boolean allSuccess);
    }

    // ==================== ADB 消息 ====================
    private static class AdbMessage {
        int command;
        int arg0;
        int arg1;
        byte[] data;
    }

    // ==================== Разрешениекоманда ====================
    private static class PermissionCommand {
        final String description;
        final String shellCommand;

        PermissionCommand(String description, String shellCommand) {
            this.description = description;
            this.shellCommand = shellCommand;
        }
    }

    // ==================== 构造方法 ====================
    public AdbPermissionHelper(Context context) {
        this.context = context.getApplicationContext();
        this.packageName = context.getPackageName();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }

    // ==================== 公Вкл API ====================

    /**
     * Вкл始автоматическипредоставить所有Разрешение
     */
    public void grantAllPermissions(Callback callback) {
        cancelled = false;
        localIdCounter = 1;
        successCount = 0;
        failCount = 0;
        executor.execute(() -> doGrantAll(callback));
    }

    /**
     * Отменавыполнение
     */
    public void cancel() {
        cancelled = true;
        closeSocket();
    }

    /**
     * 通过 ADB установка APK Файл
     * @param apkPath APK Файл 设备 绝 Путь
     */
    public void installApk(String apkPath, Callback callback) {
        cancelled = false;
        localIdCounter = 1;
        successCount = 0;
        failCount = 0;
        executor.execute(() -> doInstallApk(apkPath, callback));
    }

    // ==================== 主流程 ====================

    private void doGrantAll(Callback callback) {
        log(callback, "=== ADB: получение всех разрешений ===");

        try {
            // 0. 先отключено重连，避免 ADB  占用
            resetAdbConnection(callback);

            // 1. загрузкаили生成 RSA 密钥 （用于 ADB 认证)
            loadOrGenerateKeyPair();

            // 2. TCP Подключение（попытка多 шт.地址)
            socket = tryConnect(callback);
            if (socket == null) {
                notifyComplete(callback, false);
                return;
            }
            socketIn = socket.getInputStream();
            socketOut = socket.getOutputStream();

            // 3. ADB 握手
            if (!performHandshake(callback)) {
                log(callback, "\n✗ Ошибка подключения ADB");
                notifyComplete(callback, false);
                return;
            }

            log(callback, "✓ ADB подключён");
            log(callback, "");

            // 4. 构建并выполнениеРазрешениекоманда
            List<PermissionCommand> commands = buildCommandList();

            for (PermissionCommand cmd : commands) {
                if (cancelled || socket == null || socket.isClosed()) {
                    log(callback, "Отменено");
                    break;
                }
                executePermissionCommand(cmd, callback);
            }

            // 5. 处理无障碍Сервис（необходимо先查询再Настройки)
            if (!cancelled && socket != null && !socket.isClosed()) {
                handleAccessibilityService(callback);
            }

            // 6. 电池优化白名单
            if (!cancelled && socket != null && !socket.isClosed()) {
                handleBatteryWhitelist(callback);
            }

            // 7. 输出统计
            log(callback, "");
            log(callback, "=== Выполнение завершено ===");
            log(callback, "Успешно: " + successCount + "  Ошибка: " + failCount);
            if (failCount == 0) {
                log(callback, "Все разрешения выданы, проверьте статус");
            } else {
                log(callback, "Часть разрешений не выдана, проверьте лог выше");
            }

            notifyComplete(callback, failCount == 0);

        } catch (java.net.SocketTimeoutException e) {
            log(callback, "");
            log(callback, "✗ Таймаут подключения");
            AppLog.e(TAG, "ADB timeout", e);
            notifyComplete(callback, false);
        } catch (Exception e) {
            log(callback, "");
            log(callback, "✗ Ошибка: " + e.getMessage());
            AppLog.e(TAG, "ADB grant all failed", e);
            notifyComplete(callback, false);
        } finally {
            closeSocket();
        }
    }

    /**
     * Сброс ADB Подключение：先отключено可能существует 旧Подключение，再短暂ожидание ADB daemon 释放资源。
     *  每 раз ADB операция前调用，避免 ADB  Другое进程или残留Подключение占用导致Ошибка。
     */
    private void resetAdbConnection(Callback callback) {
        // 1. Закрыто自身可能残留 旧Подключение
        closeSocket();

        // 2. попыткаПодключение后立т.е.отключено，迫使 ADB daemon 释放现有会话
        log(callback, "Сброс подключения ADB...");
        Socket probe = null;
        try {
            probe = new Socket();
            probe.connect(new InetSocketAddress(ADB_HOST, ADB_PORT), CONNECT_TIMEOUT_MS);
            probe.close();
            probe = null;
            // ожидание ADB daemon завершениеОчистка 
            Thread.sleep(800);
            log(callback, "✓ Подключение ADB сброшено");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Ошибка подключения说明端口空闲или ADB Не Вкл启，无需Сброс
            AppLog.d(TAG, "ADB reset: port not occupied or not available");
        } finally {
            if (probe != null) {
                try { probe.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * попыткаПодключение ADB，依 разпопытка多 шт.地址：
     * 1. 127.0.0.1 (localhost)
     * 2. 设备自身  WiFi/以太网 IP 地址
     */
    private Socket tryConnect(Callback callback) {
        List<String> hosts = new ArrayList<>();
        hosts.add("127.0.0.1");

        // 收集设备自身 非回环 IPv4 地址
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp()) continue;
                java.util.Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !hosts.contains(ip)) {
                            hosts.add(ip);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLog.w(TAG, "Failed to enumerate network interfaces", e);
        }

        log(callback, "Попытка подключения ADB (порт " + ADB_PORT + ")...");

        IOException lastException = null;
        for (String host : hosts) {
            if (cancelled) break;
            try {
                log(callback, "  → " + host + ":" + ADB_PORT + " ...");
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, ADB_PORT), CONNECT_TIMEOUT_MS);
                s.setSoTimeout(READ_TIMEOUT_MS);
                log(callback, "  ✓ Подключено " + host + ":" + ADB_PORT);
                return s;
            } catch (IOException e) {
                String reason = e.getMessage();
                if (reason == null) reason = e.getClass().getSimpleName();
                log(callback, "  ✗ " + host + " - " + reason);
                lastException = e;
            }
        }

        // 所有地址всеОшибка
        log(callback, "");
        log(callback, "✗ Не удалось подключиться к ADB (все адреса не отвечают)");
        log(callback, "");
        log(callback, "Проверьте:");
        log(callback, "  1. Включена отладка USB в настройках разработчика");
        log(callback, "  2. На ПК выполнено: adb tcpip 5555");
        log(callback, "  3. Устройство подключено к WiFi (для некоторых устройств)");
        if (lastException != null) {
            AppLog.e(TAG, "ADB connect failed (all hosts)", lastException);
        }
        return null;
    }

    // ==================== APK установка流程 ====================

    private void doInstallApk(String apkPath, Callback callback) {
        log(callback, "=== ADB: установка обновления ===");

        try {
            // 先отключено重连，避免 ADB  占用
            resetAdbConnection(callback);

            loadOrGenerateKeyPair();

            socket = tryConnect(callback);
            if (socket == null) {
                notifyComplete(callback, false);
                return;
            }
            socketIn = socket.getInputStream();
            socketOut = socket.getOutputStream();

            if (!performHandshake(callback)) {
                log(callback, "\n✗ Ошибка подключения ADB");
                notifyComplete(callback, false);
                return;
            }

            log(callback, "✓ ADB подключён");
            log(callback, "");

            // /storage/emulated/<userId>/   FUSE 挂载点，跨 mount namespace 不可доступ
            // 底层真实Путь  /data/media/<userId>/，system_server 可以直接доступ
            String installPath = apkPath;
            if (installPath.startsWith("/storage/emulated/")) {
                installPath = "/data/media/" + installPath.substring("/storage/emulated/".length());
            }

            log(callback, "Установка...");
            log(callback, "  $ pm install -r " + installPath);
            log(callback, "  (установка может занять 30-60 сек, подождите)");

            // установкакоманданеобходимо更长 таймаут时间
            socket.setSoTimeout(INSTALL_TIMEOUT_MS);

            try {
                String result = executeShellCommand("pm install -r " + installPath);
                result = (result != null) ? result.trim() : "";

                if (result.toLowerCase().contains("success")) {
                    log(callback, "");
                    log(callback, "✓ Установка успешна!");
                    log(callback, "  Приложение скоро перезапустится...");
                    notifyComplete(callback, true);
                } else {
                    log(callback, "");
                    log(callback, "✗ Ошибка установки: " + result);
                    log(callback, "  Попробуйте установить вручную");
                    notifyComplete(callback, false);
                }
            } finally {
                socket.setSoTimeout(READ_TIMEOUT_MS);
            }

        } catch (java.net.SocketTimeoutException e) {
            log(callback, "");
            log(callback, "✗ Таймаут установки (> 120 сек)");
            log(callback, "  Попробуйте установить вручную");
            AppLog.e(TAG, "ADB install timeout", e);
            notifyComplete(callback, false);
        } catch (Exception e) {
            log(callback, "");
            log(callback, "✗ Ошибка: " + e.getMessage());
            AppLog.e(TAG, "ADB install failed", e);
            notifyComplete(callback, false);
        } finally {
            closeSocket();
        }
    }

    /**
     * выполнение单条Разрешениекоманда
     */
    private void executePermissionCommand(PermissionCommand cmd, Callback callback) {
        log(callback, "[" + cmd.description + "]");
        log(callback, "  $ " + cmd.shellCommand);

        try {
            String result = executeShellCommand(cmd.shellCommand);
            result = (result != null) ? result.trim() : "";

            if (result.isEmpty()) {
                log(callback, "  ✓ Успешно");
                successCount++;
            } else if (isErrorResult(result)) {
                log(callback, "  ✗ " + result);
                failCount++;
            } else {
                log(callback, "  → " + result);
                successCount++;
            }
        } catch (Exception e) {
            log(callback, "  ✗ " + e.getMessage());
            failCount++;
        }
    }

    // ==================== ADB 握手 ====================

    private boolean performHandshake(Callback callback) throws Exception {
        // Отправка CNXN 消息
        byte[] banner = "host::\0".getBytes("UTF-8");
        sendMessage(A_CNXN, A_VERSION, MAX_PAYLOAD, banner);

        // 读取响应
        AdbMessage msg = readMessage();

        // 情况1: 直接ПодключениеУспешно（无需认证)
        if (msg.command == A_CNXN) {
            serverMaxData = msg.arg1;
            logDeviceInfo(callback, msg.data);
            return true;
        }

        // 情况2: необходимо认证
        if (msg.command == A_AUTH && msg.arg0 == ADB_AUTH_TOKEN) {
            log(callback, "ADB требует авторизации...");

            // попытка用有密钥签名 token
            byte[] signedToken = signToken(msg.data);
            sendMessage(A_AUTH, ADB_AUTH_SIGNATURE, 0, signedToken);

            msg = readMessage();

            if (msg.command == A_CNXN) {
                serverMaxData = msg.arg1;
                logDeviceInfo(callback, msg.data);
                log(callback, "✓ Авторизация успешна (известный ключ)");
                return true;
            }

            // 密钥Не  识别，Отправка公钥求授权
            if (msg.command == A_AUTH) {
                log(callback, "Отправка публичного ключа, подтвердите отладку USB на устройстве...");
                byte[] pubKeyData = getAdbPublicKeyBytes();
                sendMessage(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubKeyData);

                // 延长таймаутожидание用户Подтвердить
                socket.setSoTimeout(AUTH_ACCEPT_TIMEOUT_MS);
                try {
                    msg = readMessage();
                } finally {
                    socket.setSoTimeout(READ_TIMEOUT_MS);
                }

                if (msg.command == A_CNXN) {
                    serverMaxData = msg.arg1;
                    logDeviceInfo(callback, msg.data);
                    log(callback, "✓ Авторизация успешна (пользователь подтвердил)");
                    return true;
                }
            }

            log(callback, "✗ Ошибка авторизации");
            log(callback, "  Разрешите USB-отладку на устройстве или проверьте настройки ADB");
            return false;
        }

        log(callback, "✗ Неизвестный ответ: 0x" + Integer.toHexString(msg.command));
        return false;
    }

    private void logDeviceInfo(Callback callback, byte[] data) {
        if (data != null && data.length > 0) {
            String info = new String(data).replace("\0", "").trim();
            if (!info.isEmpty()) {
                log(callback, "Устройство: " + info);
            }
        }
    }

    // ==================== 脚本выполнение（供Внешнее调用) ====================

    /**
     * 通过 ADB 协议выполнение一 шт. shell 脚本Файл，实时流式输出 д.志。
     * 用于Система白名单конфигурация等необходимовыполнение完整脚本 场景。
     *
     * @param scriptPath 脚本 设备 绝 Путь
     * @param callback   实时 д.志 и завершение回调
     */
    public void executeScriptFile(String scriptPath, Callback callback) {
        cancelled = false;
        localIdCounter = 1;
        executor.execute(() -> doExecuteScript(scriptPath, callback));
    }

    private void doExecuteScript(String scriptPath, Callback callback) {
        boolean success = attemptExecuteScript(scriptPath, callback);

        if (!success) {
            log(callback, "");
            log(callback, "========================================");
            log(callback, "[INFO] Первая попытка не удалась, повтор через 3 сек...");
            log(callback, "========================================");
            log(callback, "");
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            success = attemptExecuteScript(scriptPath, callback);
        }

        notifyComplete(callback, success);
    }

    /**
     * попыткавыполнение一 раз完整  root → remount → 脚本 流程。
     * @return true Если 脚本输出没有 [ERROR]
     */
    private boolean attemptExecuteScript(String scriptPath, Callback callback) {
        try {
            // ===== Phase 1: adb root =====
            resetAdbConnection(callback);
            loadOrGenerateKeyPair();

            socket = tryConnect(callback);
            if (socket == null) {
                return false;
            }
            socketIn = socket.getInputStream();
            socketOut = socket.getOutputStream();

            if (!performHandshake(callback)) {
                log(callback, "✗ Ошибка подключения ADB");
                return false;
            }

            log(callback, "✓ ADB подключён");
            log(callback, "");

            String idResult = executeShellCommand("id");
            boolean alreadyRoot = (idResult != null && idResult.contains("uid=0"));

            if (alreadyRoot) {
                log(callback, "[INFO] adbd уже root, пропуск adb root");
            } else {
                log(callback, "[INFO] Выполняю adb root ...");
                closeSocket();
                socket = new Socket();
                socket.connect(new InetSocketAddress(ADB_HOST, ADB_PORT), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(READ_TIMEOUT_MS);
                socketIn = socket.getInputStream();
                socketOut = socket.getOutputStream();
                performHandshake(callback);
                String rootResult = executeService("root:");
                log(callback, "[INFO]   " + rootResult);
                closeSocket();
                log(callback, "[INFO] Ожидание перезапуска adbd с правами root...");
                waitForAdbd(8000);
            }

            // ===== Phase 2: adb remount =====
            socket = new Socket();
            socket.connect(new InetSocketAddress(ADB_HOST, ADB_PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socketIn = socket.getInputStream();
            socketOut = socket.getOutputStream();

            if (performHandshake(callback)) {
                log(callback, "[INFO] Выполняю adb remount ...");
                String remountResult = executeService("remount:");
                log(callback, "[INFO]   " + remountResult);
            } else {
                log(callback, "[WARN] Ошибка remount, продолжаю выполнение скрипта");
            }
            closeSocket();
            log(callback, "");

            // ===== Phase 3: выполнение脚本 =====
            socket = new Socket();
            socket.connect(new InetSocketAddress(ADB_HOST, ADB_PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(60000);
            socketIn = socket.getInputStream();
            socketOut = socket.getOutputStream();

            if (!performHandshake(callback)) {
                log(callback, "✗ Ошибка подключения ADB");
                return false;
            }

            boolean success = executeShellCommandStreaming("sh " + scriptPath, callback);

            log(callback, "");
            if (success) {
                log(callback, "✓ Скрипт выполнен успешно");
            } else {
                log(callback, "✗ Ошибка при выполнении скрипта, проверьте логи");
            }
            return success;

        } catch (Exception e) {
            log(callback, "");
            log(callback, "✗ Ошибка: " + e.getMessage());
            AppLog.e(TAG, "Script execution failed", e);
            return false;
        } finally {
            closeSocket();
        }
    }

    /**
     * выполнение shell команда并通过回调实时输出每一行 д.志。
     * 通过检测输出 否содержит [ERROR] 来判断Успешно и 否。
     *
     * @return true Если 输出没有 [ERROR] 标记
     */
    private boolean executeShellCommandStreaming(String command, Callback callback) throws Exception {
        int localId = localIdCounter++;
        byte[] openData = ("shell:" + command + "\0").getBytes("UTF-8");
        sendMessage(A_OPEN, localId, 0, openData);

        StringBuilder lineBuffer = new StringBuilder();
        boolean hasError = false;
        boolean streamOpen = true;

        while (streamOpen) {
            AdbMessage msg = readMessage();

            switch (msg.command) {
                case A_OKAY:
                    break;

                case A_WRTE:
                    sendMessage(A_OKAY, localId, msg.arg0, null);
                    if (msg.data != null) {
                        String chunk = new String(msg.data, "UTF-8");
                        lineBuffer.append(chunk);

                        // 逐行输出завершение 行
                        int nlIndex;
                        while ((nlIndex = lineBuffer.indexOf("\n")) >= 0) {
                            String line = lineBuffer.substring(0, nlIndex);
                            lineBuffer.delete(0, nlIndex + 1);
                            log(callback, line);
                            if (line.contains("[ERROR]")) {
                                hasError = true;
                            }
                        }
                    }
                    break;

                case A_CLSE:
                    sendMessage(A_CLSE, localId, msg.arg0, null);
                    streamOpen = false;
                    break;

                default:
                    streamOpen = false;
                    break;
            }
        }

        // 输出缓冲区剩余 内容
        if (lineBuffer.length() > 0) {
            String remaining = lineBuffer.toString();
            log(callback, remaining);
            if (remaining.contains("[ERROR]")) {
                hasError = true;
            }
        }

        return !hasError;
    }

    // ==================== ADB Сервис调用（root / remount) ====================

    private String executeService(String serviceName) throws Exception {
        int localId = localIdCounter++;
        byte[] openData = (serviceName + "\0").getBytes("UTF-8");
        sendMessage(A_OPEN, localId, 0, openData);

        StringBuilder output = new StringBuilder();
        boolean streamOpen = true;

        while (streamOpen) {
            AdbMessage msg = readMessage();
            switch (msg.command) {
                case A_OKAY:
                    break;
                case A_WRTE:
                    sendMessage(A_OKAY, localId, msg.arg0, null);
                    if (msg.data != null) {
                        output.append(new String(msg.data, "UTF-8"));
                    }
                    break;
                case A_CLSE:
                    sendMessage(A_CLSE, localId, msg.arg0, null);
                    streamOpen = false;
                    break;
                default:
                    streamOpen = false;
                    break;
            }
        }
        return output.toString().trim();
    }

    private void waitForAdbd(long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        long delay = 500;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(delay); } catch (InterruptedException e) { break; }
            Socket probe = null;
            try {
                probe = new Socket();
                probe.connect(new InetSocketAddress(ADB_HOST, ADB_PORT), 1500);
                probe.close();
                return;
            } catch (Exception e) {
                delay = Math.min(delay * 2, 2000);
            } finally {
                if (probe != null) try { probe.close(); } catch (Exception ignored) {}
            }
        }
    }


    // ==================== Shell командавыполнение（Внутреннее) ====================

    /**
     * 通过 ADB 协议выполнение一条 shell команда并返回输出
     */
    private String executeShellCommand(String command) throws Exception {
        int localId = localIdCounter++;
        byte[] openData = ("shell:" + command + "\0").getBytes("UTF-8");
        sendMessage(A_OPEN, localId, 0, openData);

        StringBuilder output = new StringBuilder();
        boolean streamOpen = true;

        while (streamOpen) {
            AdbMessage msg = readMessage();

            switch (msg.command) {
                case A_OKAY:
                    // 流открыть，remoteId = msg.arg0
                    break;

                case A_WRTE:
                    // 接收команда输出数据，回复 OKAY Подтвердить
                    sendMessage(A_OKAY, localId, msg.arg0, null);
                    if (msg.data != null) {
                        output.append(new String(msg.data, "UTF-8"));
                    }
                    break;

                case A_CLSE:
                    // 流Закрыто，回复 CLSE
                    sendMessage(A_CLSE, localId, msg.arg0, null);
                    streamOpen = false;
                    break;

                default:
                    // Не 预期 消息，终止
                    streamOpen = false;
                    break;
            }
        }

        return output.toString();
    }

    // ==================== Разрешениекоманда列表 ====================

    private List<PermissionCommand> buildCommandList() {
        List<PermissionCommand> commands = new ArrayList<>();
        int sdk = Build.VERSION.SDK_INT;

        // === 基础Работа时Разрешение (pm grant) ===
        commands.add(new PermissionCommand("Разрешение камеры",
                "pm grant " + packageName + " android.permission.CAMERA"));

        commands.add(new PermissionCommand("Разрешение микрофона",
                "pm grant " + packageName + " android.permission.RECORD_AUDIO"));

        // ХранилищеРазрешение（按 API 版本区分)
        if (sdk >= 33) {
            // Android 13+: 媒体Разрешение
            commands.add(new PermissionCommand("Разрешение на видео",
                    "pm grant " + packageName + " android.permission.READ_MEDIA_VIDEO"));
            commands.add(new PermissionCommand("Разрешение на изображения",
                    "pm grant " + packageName + " android.permission.READ_MEDIA_IMAGES"));
        }
        if (sdk <= 32) {
            // Android 12 及и ниже: 传统ХранилищеРазрешение
            commands.add(new PermissionCommand("Разрешение на чтение хранилища",
                    "pm grant " + packageName + " android.permission.READ_EXTERNAL_STORAGE"));
            commands.add(new PermissionCommand("Разрешение на запись в хранилище",
                    "pm grant " + packageName + " android.permission.WRITE_EXTERNAL_STORAGE"));
        }

        // Разрешение на чтение логов
        commands.add(new PermissionCommand("Разрешение на чтение логов",
                "pm grant " + packageName + " android.permission.READ_LOGS"));

        // УведомлениеРазрешение (Android 13+)
        if (sdk >= 33) {
            commands.add(new PermissionCommand("УведомлениеРазрешение",
                    "pm grant " + packageName + " android.permission.POST_NOTIFICATIONS"));
        }

        // Разрешение Bluetooth (Android 12+)
        if (sdk >= 31) {
            commands.add(new PermissionCommand("Разрешение Bluetooth",
                    "pm grant " + packageName + " android.permission.BLUETOOTH_CONNECT"));
        }

        // === 特殊Разрешение (appops) ===
        commands.add(new PermissionCommand("Разрешение плавающего окна",
                "appops set " + packageName + " SYSTEM_ALERT_WINDOW allow"));

        // 所有Файлдоступ (Android 11+)
        if (sdk >= 30) {
            commands.add(new PermissionCommand("Доступ ко всем файлам",
                    "appops set " + packageName + " MANAGE_EXTERNAL_STORAGE allow"));
        }

        // Разрешение статистики использования（全景影像避让необходимо)
        commands.add(new PermissionCommand("Разрешение статистики использования",
                "appops set " + packageName + " android:get_usage_stats allow"));

        return commands;
    }

    // ==================== 无障碍Сервис处理 ====================

    /**
     * 处理无障碍СервисРазрешение（необходимо先查询Текущий值再追加Настройки)
     */
    private void handleAccessibilityService(Callback callback) {
        String serviceName = packageName + "/" + packageName + ".KeepAliveAccessibilityService";

        log(callback, "[Служба специальных возможностей]");

        try {
            // 查询ТекущийВключено 无障碍Сервис
            String getCmd = "settings get secure enabled_accessibility_services";
            log(callback, "  $ " + getCmd);
            String current = executeShellCommand(getCmd);
            current = (current != null) ? current.trim() : "";

            String display = (current.isEmpty() || current.equals("null")) ? "(нет)" : current;
            log(callback, "  → Текущий: " + display);

            // проверка 否Включено
            if (current.contains(packageName)) {
                log(callback, "  ✓ Включено");
                successCount++;
                return;
            }

            // 构建新值（追加而非覆盖)
            String newValue;
            if (current.isEmpty() || current.equals("null")) {
                newValue = serviceName;
            } else {
                newValue = current + ":" + serviceName;
            }

            // Настройки无障碍Сервис列表
            String putCmd = "settings put secure enabled_accessibility_services " + newValue;
            log(callback, "  $ " + putCmd);
            String result = executeShellCommand(putCmd);
            if (result != null && !result.trim().isEmpty() && isErrorResult(result.trim())) {
                log(callback, "  ✗ " + result.trim());
                failCount++;
                return;
            }

            // Включить无障碍функция
            String enableCmd = "settings put secure accessibility_enabled 1";
            log(callback, "  $ " + enableCmd);
            executeShellCommand(enableCmd);

            log(callback, "  ✓ Успешно");
            successCount++;

        } catch (Exception e) {
            log(callback, "  ✗ " + e.getMessage());
            failCount++;
        }
    }

    // ==================== 电池优化处理 ====================

    private void handleBatteryWhitelist(Callback callback) {
        log(callback, "[Белый список оптимизации батареи]");
        String cmd = "dumpsys deviceidle whitelist +" + packageName;
        log(callback, "  $ " + cmd);

        try {
            String result = executeShellCommand(cmd);
            result = (result != null) ? result.trim() : "";

            if (result.isEmpty()) {
                log(callback, "  ✓ Успешно");
                successCount++;
            } else if (result.toLowerCase().contains("added") || result.toLowerCase().contains("already")) {
                log(callback, "  ✓ " + result);
                successCount++;
            } else if (isErrorResult(result)) {
                log(callback, "  ✗ " + result);
                failCount++;
            } else {
                log(callback, "  → " + result);
                successCount++;
            }
        } catch (Exception e) {
            log(callback, "  ✗ " + e.getMessage());
            failCount++;
        }
    }

    // ==================== ADB 协议 - 消息收发 ====================

    /**
     * Отправка ADB 协议消息
     * 消息格式: command(4) + arg0(4) + arg1(4) + data_length(4) + data_checksum(4) + magic(4) + data
     */
    private void sendMessage(int command, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(24);
        header.order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(data != null ? data.length : 0);
        header.putInt(data != null ? dataChecksum(data) : 0);
        header.putInt(command ^ 0xFFFFFFFF);

        socketOut.write(header.array());
        if (data != null && data.length > 0) {
            socketOut.write(data);
        }
        socketOut.flush();
    }

    /**
     * 读取一条 ADB 协议消息
     */
    private AdbMessage readMessage() throws IOException {
        byte[] headerBytes = readFully(24);
        ByteBuffer buf = ByteBuffer.wrap(headerBytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        AdbMessage msg = new AdbMessage();
        msg.command = buf.getInt();
        msg.arg0 = buf.getInt();
        msg.arg1 = buf.getInt();
        int dataLength = buf.getInt();
        int dataCrc = buf.getInt();
        int magic = buf.getInt();

        if (dataLength > 0) {
            msg.data = readFully(dataLength);
        }

        return msg;
    }

    /**
     *  от Ввести流完整读取指定字节数
     */
    private byte[] readFully(int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = socketIn.read(data, offset, length - offset);
            if (read == -1) {
                throw new IOException("ADB Подключениеотключено");
            }
            offset += read;
        }
        return data;
    }

    /**
     * 计算数据校验 и （所有字节之 и )
     */
    private static int dataChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum;
    }

    // ==================== RSA 认证 ====================

    /**
     * загрузкаили生成 RSA 密钥 
     */
    private void loadOrGenerateKeyPair() throws Exception {
        File privateFile = new File(context.getFilesDir(), PRIVATE_KEY_FILE);
        File publicFile = new File(context.getFilesDir(), PUBLIC_KEY_FILE);

        if (privateFile.exists() && publicFile.exists()) {
            // загрузка有密钥
            byte[] privateBytes = readFileBytes(privateFile);
            byte[] publicBytes = readFileBytes(publicFile);

            KeyFactory kf = KeyFactory.getInstance("RSA");
            keyPair = new KeyPair(
                    kf.generatePublic(new X509EncodedKeySpec(publicBytes)),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(privateBytes))
            );
        } else {
            // 生成新密钥 
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(RSA_KEY_BITS);
            keyPair = kpg.generateKeyPair();

            // 持久化Сохранить
            writeFileBytes(privateFile, keyPair.getPrivate().getEncoded());
            writeFileBytes(publicFile, keyPair.getPublic().getEncoded());
        }
    }

    /**
     * использование RSA 私钥签名 ADB 认证 token
     *
     * ADB 协议，token  当作 SHA-1 摘要直接签名（PKCS#1 v1.5 + SHA-1 DigestInfo)
     */
    private byte[] signToken(byte[] token) throws Exception {
        // 构建 DigestInfo(SHA-1, token) 结构
        byte[] digestInfo = new byte[SHA1_DIGEST_INFO_PREFIX.length + token.length];
        System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA1_DIGEST_INFO_PREFIX.length);
        System.arraycopy(token, 0, digestInfo, SHA1_DIGEST_INFO_PREFIX.length, token.length);

        // использование NONEwithRSA（不再哈希，直接 PKCS#1 v1.5 签名)
        Signature sig = Signature.getInstance("NONEwithRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(digestInfo);
        return sig.sign();
    }

    /**
     * Получение ADB 格式 公钥数据
     *
     * 格式: Base64(AndroidRSAPublicKey struct) + " user@host\0"
     */
    private byte[] getAdbPublicKeyBytes() throws Exception {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        byte[] encoded = encodeAndroidRsaPublicKey(pubKey);
        String base64 = Base64.encodeToString(encoded, Base64.NO_WRAP);
        String keyStr = base64 + " adb@evcam\0";
        return keyStr.getBytes("UTF-8");
    }

    /**
     * 将 RSA 公钥编码为 Android ADB 格式
     *
     * struct RSAPublicKey {
     *     uint32_t modulus_size_words;    // 模数长度（uint32_t 为单位)
     *     uint32_t n0inv;                 // -(n^(-1)) mod 2^32
     *     uint8_t  modulus[256];          // 模数（小端序)
     *     uint8_t  rr[256];              // R^2 mod n（小端序)
     *     uint32_t exponent;              // 公钥指数
     * }
     */
    private byte[] encodeAndroidRsaPublicKey(RSAPublicKey publicKey) {
        BigInteger modulus = publicKey.getModulus();
        BigInteger exponent = publicKey.getPublicExponent();

        int modulusBytes = RSA_KEY_BITS / 8; // 256
        int modulusWords = modulusBytes / 4;  // 64

        // 计算 n0inv = -(n^(-1)) mod 2^32
        BigInteger TWO32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0 = modulus.mod(TWO32);
        BigInteger n0inv = n0.modInverse(TWO32).negate().mod(TWO32);

        // 计算 rr = (2^(2*modulusBits)) mod n
        BigInteger rr = BigInteger.ONE.shiftLeft(RSA_KEY_BITS * 2).mod(modulus);

        // 编码为小端序字节数 групп
        byte[] modulusLE = bigIntToLittleEndian(modulus, modulusBytes);
        byte[] rrLE = bigIntToLittleEndian(rr, modulusBytes);

        // 打包结构体
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + modulusBytes + modulusBytes + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(modulusWords);
        buf.putInt(n0inv.intValue());
        buf.put(modulusLE);
        buf.put(rrLE);
        buf.putInt(exponent.intValue());

        return buf.array();
    }

    /**
     * 将 BigInteger 转换为小端序字节数 групп
     */
    private static byte[] bigIntToLittleEndian(BigInteger value, int length) {
        byte[] bigEndian = value.toByteArray(); // 大端序，可能有前导 0x00
        byte[] result = new byte[length];

        // 跳过 BigInteger 可能添加 符号位前导零
        int srcLen = bigEndian.length;
        if (srcLen > length && bigEndian[0] == 0) {
            srcLen--;
        }

        // 逆序复制（大端 → 小端)
        int copyLen = Math.min(srcLen, length);
        for (int i = 0; i < copyLen; i++) {
            result[i] = bigEndian[bigEndian.length - 1 - i];
        }

        return result;
    }

    // ==================== инструмент方法 ====================

    private boolean isErrorResult(String result) {
        String lower = result.toLowerCase();
        return lower.contains("exception") ||
                lower.contains("error") ||
                lower.contains("unknown permission") ||
                lower.contains("not found") ||
                lower.contains("failure") ||
                lower.contains("security") ||
                lower.contains("not allowed");
    }

    private void log(Callback callback, String message) {
        mainHandler.post(() -> callback.onLog(message));
    }

    private void notifyComplete(Callback callback, boolean allSuccess) {
        mainHandler.post(() -> callback.onComplete(allSuccess));
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
        socketIn = null;
        socketOut = null;
    }

    private static byte[] readFileBytes(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read == -1) break;
                offset += read;
            }
        }
        return data;
    }

    private static void writeFileBytes(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }
}
