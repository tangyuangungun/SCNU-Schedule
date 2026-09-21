package cn.edu.scnu.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AppPrefs {
    public static final String FREQ_MANUAL = "manual";
    public static final String FREQ_WEEKLY = "weekly";
    public static final String FREQ_MONTHLY = "monthly";
    public static final String REMINDER_TYPE_ALARM = "alarm";
    public static final String REMINDER_TYPE_MESSAGE = "message";

    private static final String FILE = "scnu_schedule";
    private static final String ACCOUNT = "account";
    private static final String PASSWORD = "password";
    private static final String REMEMBER = "remember";
    private static final String COURSES = "courses";
    private static final String SEMESTER = "semester";
    private static final String LAST_UPDATE = "last_update";
    private static final String DARK = "dark_mode";
    private static final String FREQ = "update_frequency";
    private static final String REMINDERS = "reminder_minutes";
    private static final String REMINDER_TYPE = "reminder_type";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void saveCredentials(Context context, String account, String password, boolean remember) {
        prefs(context).edit()
                .putString(ACCOUNT, account)
                .putString(PASSWORD, password)
                .putBoolean(REMEMBER, remember)
                .apply();
    }

    public static String account(Context context) {
        return prefs(context).getString(ACCOUNT, "");
    }

    public static String password(Context context) {
        return prefs(context).getString(PASSWORD, "");
    }

    public static boolean remember(Context context) {
        return prefs(context).getBoolean(REMEMBER, true);
    }

    public static boolean isLoggedIn(Context context) {
        return !account(context).isEmpty() && !password(context).isEmpty();
    }

    public static void clearCredentials(Context context) {
        prefs(context).edit().remove(ACCOUNT).remove(PASSWORD).remove(REMEMBER).apply();
    }

    public static void saveCourses(Context context, String semester, List<Course> courses) {
        JSONArray array = new JSONArray();
        for (Course c : courses) {
            try {
                array.put(c.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs(context).edit()
                .putString(COURSES, array.toString())
                .putString(SEMESTER, semester == null ? "" : semester)
                .putLong(LAST_UPDATE, System.currentTimeMillis())
                .apply();
    }

    public static List<Course> courses(Context context) {
        List<Course> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(COURSES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) result.add(Course.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static String semester(Context context) {
        return prefs(context).getString(SEMESTER, "");
    }

    public static long lastUpdate(Context context) {
        return prefs(context).getLong(LAST_UPDATE, 0L);
    }

    public static boolean darkMode(Context context) {
        return prefs(context).getBoolean(DARK, false);
    }

    public static void setDarkMode(Context context, boolean dark) {
        prefs(context).edit().putBoolean(DARK, dark).apply();
    }

    public static String updateFrequency(Context context) {
        return prefs(context).getString(FREQ, FREQ_WEEKLY);
    }

    public static void setUpdateFrequency(Context context, String frequency) {
        prefs(context).edit().putString(FREQ, frequency).apply();
    }

    public static Set<Integer> reminderMinutes(Context context) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        String raw = prefs(context).getString(REMINDERS, "30,15");
        for (String part : raw.split(",")) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value > 0) values.add(value);
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    public static void setReminderMinutes(Context context, Set<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (Integer value : values) {
            if (builder.length() > 0) builder.append(',');
            builder.append(value);
        }
        prefs(context).edit().putString(REMINDERS, builder.toString()).apply();
    }

    public static String reminderType(Context context) {
        return prefs(context).getString(REMINDER_TYPE, REMINDER_TYPE_MESSAGE);
    }

    public static void setReminderType(Context context, String type) {
        if (!REMINDER_TYPE_ALARM.equals(type)) type = REMINDER_TYPE_MESSAGE;
        prefs(context).edit().putString(REMINDER_TYPE, type).apply();
    }

    public static String reminderTypeLabel(String type) {
        return REMINDER_TYPE_ALARM.equals(type) ? "闹钟提醒" : "消息提醒";
    }

    public static String frequencyLabel(String value) {
        if (FREQ_MANUAL.equals(value)) return "仅手动更新";
        if (FREQ_MONTHLY.equals(value)) return "每月自动更新";
        return "每周自动更新";
    }
}
