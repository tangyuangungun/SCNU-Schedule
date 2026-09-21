package cn.edu.scnu.schedule;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.time.LocalDate;
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
        else if (variant == VARIANT_WEEK_STRIP) fillWeekStrip(views, semester, visible);
        else fillWeekGrid(views, semester, visible, today);
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

        views.setTextViewText(R.id.widget_title, monthDay(today) + " " + weekdayShort(weekday));
        String status = "第" + semester.weekFor(today) + "周 · " + todayCourses.size() + "门课程";
        views.setTextViewText(R.id.widget_subtitle, status);

        int[] ids = {R.id.widget_course_1, R.id.widget_course_2, R.id.widget_course_3};
        for (int i = 0; i < ids.length; i++) {
            if (i < todayCourses.size()) {
                views.setViewVisibility(ids[i], android.view.View.VISIBLE);
                views.setTextViewText(ids[i], compactCourse(todayCourses.get(i), true));
            } else {
                views.setViewVisibility(ids[i], android.view.View.GONE);
            }
        }
        views.setViewVisibility(R.id.widget_empty, todayCourses.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private static void fillWeekStrip(RemoteViews views, SemesterCalendar.Semester semester, List<Course> courses) {
        views.setTextViewText(R.id.widget_title, "第" + semester.weekFor(LocalDate.now()) + "周");
        views.setTextViewText(R.id.widget_subtitle,
                shortDate(semester.weekStart(semester.weekFor(LocalDate.now()))) + " - "
                        + shortDate(semester.weekEnd(semester.weekFor(LocalDate.now()))));
        int[] ids = {R.id.widget_day_1, R.id.widget_day_2, R.id.widget_day_3, R.id.widget_day_4,
                R.id.widget_day_5, R.id.widget_day_6, R.id.widget_day_7};
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (int day = 1; day <= 7; day++) {
            List<Course> dayCourses = new ArrayList<>();
            for (Course course : courses) if (course.weekdayNumber() == day) dayCourses.add(course);
            dayCourses.sort(Comparator.comparingInt(Course::periodStart));
            String hint = dayCourses.isEmpty() ? "无课" : abbreviate(dayCourses.get(0).name, 4);
            views.setTextViewText(ids[day - 1], names[day - 1] + "\n" + dayCourses.size() + "节\n" + hint);
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength) + "…";
    }
    private static void fillWeekGrid(RemoteViews views, SemesterCalendar.Semester semester,
                                     List<Course> courses, LocalDate today) {
        int week = semester.weekFor(today);
        LocalDate monday = semester.weekStart(week);
        views.setTextViewText(R.id.widget_title, "第" + week + "周 · " + shortDate(monday) + " - "
                + shortDate(semester.weekEnd(week)));
        views.setTextViewText(R.id.widget_subtitle, "今天 " + weekdayShort(today.getDayOfWeek().getValue())
                + " · 本周 " + courses.size() + "门课程");

        int[] ids = {R.id.widget_week_day_1, R.id.widget_week_day_2, R.id.widget_week_day_3,
                R.id.widget_week_day_4, R.id.widget_week_day_5, R.id.widget_week_day_6,
                R.id.widget_week_day_7};
        String[] names = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        for (int day = 1; day <= 7; day++) {
            List<Course> dayCourses = new ArrayList<>();
            for (Course course : courses) if (course.weekdayNumber() == day) dayCourses.add(course);
            dayCourses.sort(Comparator.comparingInt(Course::periodStart));
            StringBuilder text = new StringBuilder(names[day - 1]).append(' ');
            text.append(monthDay(monday.plusDays(day - 1L))).append("  ");
            if (dayCourses.isEmpty()) {
                text.append("无课");
            } else {
                text.append(dayCourses.size()).append("门 · ");
                for (int i = 0; i < Math.min(2, dayCourses.size()); i++) {
                    if (i > 0) text.append("、");
                    text.append(dayCourses.get(i).name);
                }
                if (dayCourses.size() > 2) text.append(" 等");
            }
            views.setTextViewText(ids[day - 1], text.toString());
        }
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

    private static String compactCourse(Course course, boolean withLocation) {
        String time = course.time == null || course.time.isEmpty() ? "时间待定" : course.time;
        String title = time + "  " + safe(course.name);
        if (withLocation && course.location != null && !course.location.isEmpty()) {
            title += "\n" + course.location;
        }
        return title;
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

