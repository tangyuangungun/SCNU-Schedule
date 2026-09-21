package cn.edu.scnu.schedule;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class CalendarImporter {
    public interface Callback {
        void onDone(String message);
    }

    private static final String MARK = "[华师课表]";

    private CalendarImporter() {}

    public static boolean hasPermission(Context context) {
        int read = context.checkSelfPermission(Manifest.permission.READ_CALENDAR);
        int write = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR);
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED;
    }

    public static void importCourses(Context context, List<Course> courses, Callback callback) {
        if (!hasPermission(context)) {
            callback.onDone("没有日历读写权限");
            return;
        }
        if (courses.isEmpty()) {
            callback.onDone("课表为空，请先更新课表");
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        long calendarId = findWritableCalendar(resolver);
        if (calendarId < 0) {
            callback.onDone("手机中没有可写的本地日历，请先在系统日历中添加一个账户");
            return;
        }

        int inserted = 0;
        int skipped = 0;
        for (Course course : courses) {
            if (insertCourse(resolver, calendarId, course)) inserted++;
            else skipped++;
        }
        callback.onDone("已导入 " + inserted + " 门课程" + (skipped > 0 ? "，跳过 " + skipped + " 个重复项" : ""));
    }

    private static long findWritableCalendar(ContentResolver resolver) {
        Uri uri = CalendarContract.Calendars.CONTENT_URI;
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE
        };
        String selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ? AND "
                + CalendarContract.Calendars.VISIBLE + " = 1";
        String[] args = {String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
        try (Cursor cursor = resolver.query(uri, projection, selection, args,
                CalendarContract.Calendars.IS_PRIMARY + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static boolean insertCourse(ContentResolver resolver, long calendarId, Course course) {
        if (course.firstClassDate.isEmpty() || course.time.isEmpty()) return false;
        long startMillis;
        long endMillis;
        try {
            String[] parts = course.time.split("-");
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            startMillis = format.parse(course.firstClassDate + " " + parts[0].trim()).getTime();
            endMillis = format.parse(course.firstClassDate + " " + parts[1].trim()).getTime();
        } catch (Exception e) {
            return false;
        }

        if (exists(resolver, calendarId, course, startMillis)) return false;

        String description = MARK + " 任课教师：" + course.teacher + "\n周次：" + course.weeks
                + "\n节次：" + course.periods + "\n课程代码：" + course.code;
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, course.name);
        values.put(CalendarContract.Events.DESCRIPTION, description);
        values.put(CalendarContract.Events.EVENT_LOCATION, course.location);
        values.put(CalendarContract.Events.DTSTART, startMillis);
        values.put(CalendarContract.Events.DTEND, endMillis);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        values.put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;COUNT=" + course.weekCount());
        Uri uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values);
        if (uri == null) return false;

        long eventId = ContentUris.parseId(uri);
        ContentValues reminder = new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
        reminder.put(CalendarContract.Reminders.MINUTES, 30);
        reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
        try {
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder);
        } catch (Exception ignored) {
        }
        return true;
    }

    private static boolean exists(ContentResolver resolver, long calendarId, Course course, long startMillis) {
        String selection = CalendarContract.Events.CALENDAR_ID + "=? AND "
                + CalendarContract.Events.TITLE + "=? AND "
                + CalendarContract.Events.DTSTART + "=? AND "
                + CalendarContract.Events.DESCRIPTION + " LIKE ?";
        String[] args = {
                String.valueOf(calendarId),
                course.name,
                String.valueOf(startMillis),
                MARK + "%"
        };
        try (Cursor cursor = resolver.query(CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID}, selection, args, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }
}
