package com.kooo.evcam;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logcat 信号观察者
 * 专门解析转 к 灯Система д.志信号
 *
 * использование logcat -e  原生层面过滤，只输出匹配  д.志行。
 * 这样т.е.使行驶Система д.志量暴增，также不会影响转 к 灯信号 响应速度。
 */
public class LogcatSignalObserver {
    private static final String TAG = "LogcatSignalObserver";
    
    private static final Pattern SIGNAL_PATTERN = Pattern.compile("data1 = (\\d+)");

    public interface SignalListener {
        /**
         * 原始 д.志回调
         * @param line 完整logcat行
         * @param data1 解析 до   data1（Не 解析 до 则为 -1)
         */
        void onLogLine(String line, int data1);
    }

    private final SignalListener listener;
    private Thread logcatThread;
    private Process logcatProcess;
    private volatile boolean isRunning = false;
    private String[] filterKeywords;

    public LogcatSignalObserver(SignalListener listener) {
        this.listener = listener;
    }

    /**
     * Настройки过滤Выкл键字列表（用于构建 logcat -e 正则)。
     * 必须  start() до调用。
     * @param keywords 转 к 灯相ВыклВыкл键字（если左转触发词、右转触发词)
     */
    public void setFilterKeywords(String... keywords) {
        this.filterKeywords = keywords;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        
        logcatThread = new Thread(() -> {
            Process process = null;
            BufferedReader reader = null;
            try {
                // использование -T 参数 от Текущий时间Вкл始读取，完全跳过历史缓冲区。
                // 避免冷Запуск时读 до 旧 转 к 灯信号导致误触发补盲画面。
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
                String now = sdf.format(new Date());

                // 构建 logcat -e 正则过滤表达式， 原生层面只输出匹配 行。
                // 行驶车机 д.志量可能每 сек.数千行，不做原生过滤会导致转 к 灯信号延迟。
                String regexFilter = buildLogcatRegex();

                List<String> cmd = new ArrayList<>();
                cmd.add("logcat");
                cmd.add("-v");
                cmd.add("brief");
                cmd.add("-T");
                cmd.add(now);
                if (regexFilter != null) {
                    cmd.add("-e");
                    cmd.add(regexFilter);
                }
                cmd.add("*:V");

                AppLog.d(TAG, "Logcat command: " + cmd);

                process = Runtime.getRuntime().exec(cmd.toArray(new String[0]));
                logcatProcess = process;
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    int data1 = -1;
                    if (line.contains("data1 =")) {
                        Matcher matcher = SIGNAL_PATTERN.matcher(line);
                        if (matcher.find()) {
                            try {
                                data1 = Integer.parseInt(matcher.group(1));
                            } catch (NumberFormatException e) {
                                data1 = -1;
                            }
                        }
                    }
                    if (listener != null) {
                        listener.onLogLine(line, data1);
                    }
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Logcat reading error: " + e.getMessage());
            } finally {
                isRunning = false;
                try {
                    if (reader != null) reader.close();
                    if (process != null) process.destroy();
                } catch (Exception e) {
                    // Ignore
                }
                logcatProcess = null;
            }
        });
        logcatThread.setPriority(Thread.MAX_PRIORITY);
        logcatThread.start();
    }

    /**
     * 构建 logcat -e использование 正则表达式。
     * 将用户конфигурация 触发Выкл键字 и 内置  "data1 =" режим合并为一 шт. OR 正则。
     * logcat -e   C 层过滤，效率远Высокий于 Java 层逐行проверка。
     *
     * @return 正则字符串，若无действуетВыкл键字则Возвращает null（不过滤)
     */
    private String buildLogcatRegex() {
        // использование Set 去重
        Set<String> parts = new HashSet<>();

        // 内置 data1 режим（转 к 灯通用信号)
        parts.add("data1 =");

        // 内置转 к 灯СтатусВыкл键字（用于检测转 к 灯Закрыто)
        parts.add("front turn signal:");

        // 用户自定义触发Выкл键字
        if (filterKeywords != null) {
            for (String keyword : filterKeywords) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    // 提取Выкл键字最具区分度 固定部分作为过滤条件
                    // 例если "left front turn signal:1" ->   "front turn signal:" 覆盖
                    // 但Если 用户Настройки完全不同 Выкл键字，необходимо单独加入
                    String trimmed = keyword.trim();
                    boolean coveredByBuiltin = false;
                    for (String builtin : new String[]{"data1 =", "front turn signal:"}) {
                        if (trimmed.contains(builtin)) {
                            coveredByBuiltin = true;
                            break;
                        }
                    }
                    if (!coveredByBuiltin) {
                        //  正则特殊字符进行转义
                        parts.add(escapeRegex(trimmed));
                    }
                }
            }
        }

        if (parts.isEmpty()) return null;

        // 用 "|" Подключение为 OR 表达式
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append("|");
            sb.append(part);
        }
        return sb.toString();
    }

    /**
     * 转义正则特殊字符（logcat -e использование POSIX ERE)
     */
    private static String escapeRegex(String input) {
        // 只转义  logcat 正则有特殊含义 字符
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public boolean isAlive() {
        return isRunning && logcatThread != null && logcatThread.isAlive();
    }

    public void stop() {
        isRunning = false;
        // 先销毁进程，使 readLine() 立т.е.Возвращает null  от 而Выход循环
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
        if (logcatThread != null) {
            logcatThread.interrupt();
            logcatThread = null;
        }
    }
}
