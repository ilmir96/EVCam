package com.kooo.evcam.playback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 *  д.期分 групп模型
 * 将同一天 Видео/Изображение групп聚合 一起
 * @param <T> VideoGroup или PhotoGroup
 */
public class DateSection<T> {
    
    /**  д.期字符串，格式为 yyyy-MM-dd */
    private final String dateString;
    
    /**  д.期 象 */
    private final Date date;
    
    /** 该 д.期 所有 групп */
    private final List<T> items;
    
    /**  否展Вкл */
    private boolean expanded;
    
    public DateSection(String dateString, Date date) {
        this.dateString = dateString;
        this.date = date;
        this.items = new ArrayList<>();
        this.expanded = isToday(date); // 只有СегодняПо умолчанию展Вкл
    }
    
    /**
     * 判断指定 д.期 否 Сегодня
     */
    private boolean isToday(Date date) {
        Calendar today = Calendar.getInstance();
        Calendar targetDate = Calendar.getInstance();
        targetDate.setTime(date);
        
        return today.get(Calendar.YEAR) == targetDate.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == targetDate.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * 添加一 шт. групп до 此 д.期
     */
    public void addItem(T item) {
        items.add(item);
    }
    
    /**
     * Получение д.期字符串
     */
    public String getDateString() {
        return dateString;
    }
    
    /**
     * Получение д.期 象
     */
    public Date getDate() {
        return date;
    }
    
    /**
     * Получение该 д.期 所有 групп
     */
    public List<T> getItems() {
        return items;
    }
    
    /**
     * Получение групп数量
     */
    public int getItemCount() {
        return items.size();
    }
    
    /**
     *  否展Вкл
     */
    public boolean isExpanded() {
        return expanded;
    }
    
    /**
     * Настройки展ВклСтатус
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
    
    /**
     * 切换展Вкл/收起Статус
     */
    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }
    
    /**
     * Получение格式化  д.期显示字符串
     * Сегодня显示"Сегодня"，Вчера显示"Вчера"，Другое显示 д.期
     */
    public String getFormattedDateDisplay() {
        Calendar today = Calendar.getInstance();
        Calendar targetDate = Calendar.getInstance();
        targetDate.setTime(date);
        
        // очистка时间部分，只比较 д.期
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        
        targetDate.set(Calendar.HOUR_OF_DAY, 0);
        targetDate.set(Calendar.MINUTE, 0);
        targetDate.set(Calendar.SECOND, 0);
        targetDate.set(Calendar.MILLISECOND, 0);
        
        long diffInDays = (today.getTimeInMillis() - targetDate.getTimeInMillis()) / (24 * 60 * 60 * 1000);
        
        if (diffInDays == 0) {
            return "Сегодня";
        } else if (diffInDays == 1) {
            return "Вчера";
        } else if (diffInDays == 2) {
            return "Позавчера";
        } else {
            // 判断 否 同一 г. 
            if (today.get(Calendar.YEAR) == targetDate.get(Calendar.YEAR)) {
                SimpleDateFormat sdf = new SimpleDateFormat("MM мес. dd д.", Locale.CHINESE);
                return sdf.format(date);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy г. MM мес. dd д.", Locale.CHINESE);
                return sdf.format(date);
            }
        }
    }
    
    /**
     * Получение星期几
     */
    public String getDayOfWeek() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.CHINESE);
        return sdf.format(date);
    }
    
    /**
     * Получение带星期 完整 д.期显示
     */
    public String getFullDateDisplay() {
        String dateDisplay = getFormattedDateDisplay();
        String dayOfWeek = getDayOfWeek();
        
        // Сегодня、Вчера、Позавчера显示时带星期
        if ("Сегодня".equals(dateDisplay) || "Вчера".equals(dateDisplay) || "Позавчера".equals(dateDisplay)) {
            return dateDisplay + " · " + dayOfWeek;
        }
        
        return dateDisplay + " " + dayOfWeek;
    }
}
