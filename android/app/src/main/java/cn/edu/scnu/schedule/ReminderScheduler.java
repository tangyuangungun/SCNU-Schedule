package cn.edu.scnu.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

public final class ReminderScheduler {
    private static final int[] POSSIBLE_LEADS = {5, 10, 15, 30, 60};

    private ReminderScheduler() {}

    public static void scheduleAll(Context context) {
        List<Course> courses = AppPrefs.courses(context);
        Set<Integer> minutes = AppPrefs.reminderMinutes(context);
        cancelAll(context, courses);
        for (int i = 0; i < courses.size(); i++) {
            for (Integer lead : minutes) {
                scheduleCourse(context, i, lead);
            }
        }
    }

    public static void cancelAll(Context context, List<Course> courses) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        for (int i = 0; i < Math.max(courses.size(), 120); i++) {
            for (int lead : POSSIBLE_LEADS) {
                manager.cancel(pending(context, i, lead));
            }
        }
    }

    public static void scheduleCourse(Context context, int index, int leadMinutes) {
        List<Course> courses = AppPrefs.courses(context);
        if (index < 0 || index >= courses.size()) return;
        long trigger = nextTrigger(courses.get(index), leadMinutes);
        if (trigger <= 0) return;

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = pending(context, index, leadMinutes);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, trigger, pending);
            }
        } catch (SecurityException ignored) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
        }
    }

    private static PendingIntent pending(Context context, int index, int lead) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("course_index", index);
        intent.putExtra("lead_minutes", lead);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode(index, lead), intent, flags);
    }

    private static int requestCode(int index, int lead) {
        return index * 100 + lead;
    }

    static long nextTrigger(Course course, int leadMinutes) {
        java.util.Date first = course.firstDate();
        if (first == null || course.time == null || course.time.isEmpty()) return -1L;

        Calendar firstClass = Calendar.getInstance();
        firstClass.setTime(first);
        applyTime(firstClass, course.time.substring(0, Math.min(5, course.time.length())));

        Calendar endDate = (Calendar) firstClass.clone();
        endDate.add(Calendar.DAY_OF_YEAR, Math.max(0, course.weekCount() - 1) * 7);
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);

        Calendar candidate = (Calendar) firstClass.clone();
        Calendar now = Calendar.getInstance();
        while (candidate.before(now)) {
            candidate.add(Calendar.DAY_OF_YEAR, 7);
        }

        for (int i = 0; i < 30; i++) {
            if (candidate.after(endDate)) return -1L;
            long trigger = candidate.getTimeInMillis() - leadMinutes * 60_000L;
            if (trigger > now.getTimeInMillis()) return trigger;
            candidate.add(Calendar.DAY_OF_YEAR, 7);
        }
        return -1L;
    }

    private static void applyTime(Calendar calendar, String time) {
        try {
            String start = time.split("-")[0].trim();
            String[] parts = start.split(":");
            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            calendar.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
        } catch (Exception ignored) {
        }
    }
}
