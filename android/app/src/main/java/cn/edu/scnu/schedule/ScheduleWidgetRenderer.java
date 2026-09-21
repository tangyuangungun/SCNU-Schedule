package cn.edu.scnu.schedule;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ScheduleWidgetRenderer {
    static final int VARIANT_TODAY = 0;
    static final int VARIANT_WEEK_STRIP = 1;
    static final int VARIANT_WEEK_GRID = 2;

    private ScheduleWidgetRenderer() {}

    static RemoteViews create(Context context, int variant, int layoutId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 7000 + variant, intent, flags);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        LocalDate today = LocalDate.now();
        SemesterCalendar.Semester semester = SemesterCalendar.resolve(today);
        int currentWeek = semester.weekFor(today);
        List<Course> courses = AppPrefs.courses(context);
        List<Course> visible = coursesForWeek(courses, currentWeek);

        if (variant == VARIANT_TODAY) fillToday(context, views, semester, visible, today);
        else if (variant == VARIANT_WEEK_STRIP) fillWeekStrip(context, views, semester, visible, today);
        else fillWeekGrid(context, views, semester, visible, today);
        return views;
    }

    private static void fillToday(Context context, RemoteViews views, SemesterCalendar.Semester semester,
                                  List<Course> weekCourses, LocalDate today) {
        List<Course> todayCourses = new ArrayList<>();
        int weekday = today.getDayOfWeek().getValue();
        for (Course course : weekCourses) {
            if (course.weekdayNumber() == weekday) todayCourses.add(course);
        }
        todayCourses.sort(Comparator.comparingInt(Course::periodStart));

        views.setTextViewText(R.id.widget_title, "今天 · " + weekdayShort(weekday));
        views.setTextViewText(R.id.widget_subtitle,
                monthDay(today) + " · 第" + semester.weekFor(today) + "周 · " + todayCourses.size() + "门");

        Course focus = focusCourse(todayCourses, LocalTime.now());
        if (focus == null) {
            views.setViewVisibility(R.id.widget_course_1, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_course_2, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_course_1, android.view.View.VISIBLE);
            views.setTextViewText(R.id.widget_course_1, compactCourse(focus));
            views.setViewVisibility(R.id.widget_course_2, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE);
        }
    }

    private static void fillWeekStrip(Context context, RemoteViews views, SemesterCalendar.Semester semester,
                                      List<Course> courses, LocalDate today) {
        int week = semester.weekFor(today);
        LocalDate monday = semester.weekStart(week);
        views.setTextViewText(R.id.widget_title, "第" + week + "周");
        views.setTextViewText(R.id.widget_subtitle,
                shortDate(monday) + " - " + shortDate(semester.weekEnd(week)) + " · " + courses.size() + "门");
        int[] ids = {R.id.widget_day_1, R.id.widget_day_2, R.id.widget_day_3, R.id.widget_day_4,
                R.id.widget_day_5, R.id.widget_day_6, R.id.widget_day_7};
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (int day = 1; day <= 7; day++) {
            LocalDate date = monday.plusDays(day - 1L);
            List<Course> dayCourses = coursesForDay(courses, day);
            String hint;
            if (dayCourses.isEmpty()) {
                hint = "无课";
            } else {
                Course first = dayCourses.get(0);
                hint = startTime(first) + " " + abbreviate(first.name, 5);
            }
            views.setTextViewText(ids[day - 1], names[day - 1] + " " + shortDate(date)
                    + "\n" + dayCourses.size() + "门\n" + hint);
            views.setTextColor(ids[day - 1], context.getColor(
                    date.equals(today) ? R.color.widget_accent : R.color.widget_title));
        }
    }

    private static void fillWeekGrid(Context context, RemoteViews views, SemesterCalendar.Semester semester,
                                     List<Course> courses, LocalDate today) {
        int week = semester.weekFor(today);
        LocalDate monday = semester.weekStart(week);
        views.setTextViewText(R.id.widget_title, "第" + week + "周 · " + shortDate(monday)
                + " - " + shortDate(semester.weekEnd(week)));
        views.setTextViewText(R.id.widget_subtitle,
                "今天 " + weekdayShort(today.getDayOfWeek().getValue()) + " · 本周 " + courses.size() + "门");

        int[] ids = {R.id.widget_week_day_1, R.id.widget_week_day_2, R.id.widget_week_day_3,
                R.id.widget_week_day_4, R.id.widget_week_day_5, R.id.widget_week_day_6,
                R.id.widget_week_day_7};
        String[] names = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        for (int day = 1; day <= 7; day++) {
            LocalDate date = monday.plusDays(day - 1L);
            List<Course> dayCourses = coursesForDay(courses, day);
            StringBuilder text = new StringBuilder(names[day - 1]).append(' ').append(shortDate(date)).append('\n');
            if (dayCourses.isEmpty()) {
                text.append("无课");
            } else {
                int shown = Math.min(2, dayCourses.size());
                for (int i = 0; i < shown; i++) {
                    if (i > 0) text.append(" · ");
                    Course course = dayCourses.get(i);
                    text.append(startTime(course)).append(' ').append(abbreviate(course.name, 8));
                }
                if (dayCourses.size() > shown) text.append(" 等").append(dayCourses.size()).append("门");
            }
            views.setTextViewText(ids[day - 1], text.toString());
            views.setTextColor(ids[day - 1], context.getColor(
                    date.equals(today) ? R.color.widget_accent : R.color.widget_title));
        }
    }

    private static List<Course> coursesForDay(List<Course> courses, int day) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course.weekdayNumber() == day) result.add(course);
        }
        result.sort(Comparator.comparingInt(Course::periodStart));
        return result;
    }

    private static List<Course> coursesForWeek(List<Course> courses, int week) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course.occursInWeek(week) && course.weekdayNumber() >= 1 && course.weekdayNumber() <= 7) {
                result.add(course);
            }
        }
        result.sort((a, b) -> {
            int day = Integer.compare(a.weekdayNumber(), b.weekdayNumber());
            return day != 0 ? day : Integer.compare(a.periodStart(), b.periodStart());
        });
        return result;
    }

    private static Course focusCourse(List<Course> courses, LocalTime now) {
        if (courses.isEmpty()) return null;
        for (Course course : courses) {
            LocalTime start = courseStart(course);
            if (start == null || !start.isBefore(now)) return course;
        }
        return courses.get(courses.size() - 1);
    }

    private static LocalTime courseStart(Course course) {
        try {
            String value = course.time == null ? "" : course.time.trim();
            int dash = value.indexOf('-');
            if (dash > 0) value = value.substring(0, dash).trim();
            return value.isEmpty() ? null : LocalTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String compactCourse(Course course) {
        String location = cleanLocation(course.location);
        return safe(course.name) + "\n" + courseTime(course)
                + (location.isEmpty() ? "" : "\n" + location);
    }

    private static String courseTime(Course course) {
        if (course.time != null && !course.time.isEmpty()) return course.time;
        if (course.periods != null && !course.periods.isEmpty()) return course.periods + "节";
        return "时间待定";
    }

    private static String startTime(Course course) {
        String value = course.time == null ? "" : course.time.trim();
        int dash = value.indexOf('-');
        if (dash > 0) value = value.substring(0, dash).trim();
        if (!value.isEmpty()) return value;
        return course.periods == null || course.periods.isEmpty() ? "时间待定" : course.periods + "节";
    }

    private static String cleanLocation(String location) {
        if (location == null) return "";
        String value = location.replace("南海校园", "").replace("大学城校园", "").trim();
        return abbreviate(value, 22);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength) + "…";
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "未填写" : value;
    }

    private static String monthDay(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private static String shortDate(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private static String weekdayShort(int weekday) {
        String[] names = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return weekday >= 1 && weekday <= 7 ? names[weekday] : "";
    }
}
