package cn.edu.scnu.schedule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CourseParser {
    private static final Map<Integer, String[]> PERIOD_TIMES = new HashMap<>();
    static {
        PERIOD_TIMES.put(1, new String[]{"08:30", "09:10"});
        PERIOD_TIMES.put(2, new String[]{"09:20", "10:00"});
        PERIOD_TIMES.put(3, new String[]{"10:20", "11:00"});
        PERIOD_TIMES.put(4, new String[]{"11:10", "11:50"});
        PERIOD_TIMES.put(5, new String[]{"14:00", "14:40"});
        PERIOD_TIMES.put(6, new String[]{"14:50", "15:30"});
        PERIOD_TIMES.put(7, new String[]{"15:40", "16:20"});
        PERIOD_TIMES.put(8, new String[]{"16:30", "17:10"});
        PERIOD_TIMES.put(9, new String[]{"19:00", "19:40"});
        PERIOD_TIMES.put(10, new String[]{"19:50", "20:30"});
        PERIOD_TIMES.put(11, new String[]{"20:40", "21:30"});
    }

    private static final Pattern SCHEDULE_PATTERN = Pattern.compile(
            "(\\S+周)\\s*(星期[一二三四五六日天])\\[([^\\]]+)\\]\\s*(.*?)(?=\\s+\\S+周\\s*星期[一二三四五六日天]\\[|$)"
    );

    private CourseParser() {}

    public static List<Course> parse(JSONArray headers, JSONArray rows) {
        List<Course> result = new ArrayList<>();
        if (headers == null || rows == null) return result;
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.length(); i++) {
            index.put(headers.optString(i).trim(), i);
        }

        for (int r = 0; r < rows.length(); r++) {
            JSONArray row = rows.optJSONArray(r);
            if (row == null) continue;
            String name = cell(row, index, "课程名称");
            if (name == null || name.trim().isEmpty()) continue;

            Course c = new Course();
            c.code = safe(cell(row, index, "课程代码"));
            c.name = safe(name);
            c.className = safe(cell(row, index, "班级名称"));
            c.hours = safe(cell(row, index, "课程学时"));
            c.credits = safe(cell(row, index, "学分"));
            c.campus = safe(cell(row, index, "校区"));
            c.department = safe(cell(row, index, "开课单位"));
            c.teacher = safe(cell(row, index, "任课教师"));
            c.firstClassDate = safe(cell(row, index, "首次上课日期"));
            c.scheduleText = safe(cell(row, index, "上课时间地点"));
            parseMeeting(c);
            result.add(c);
        }

        Collections.sort(result, (a, b) -> {
            int w = Integer.compare(a.weekdayNumber(), b.weekdayNumber());
            return w != 0 ? w : Integer.compare(a.periodStart(), b.periodStart());
        });
        return result;
    }

    private static String cell(JSONArray row, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i < 0 || i >= row.length()) return "";
        return row.optString(i, "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void parseMeeting(Course c) {
        Matcher matcher = SCHEDULE_PATTERN.matcher(c.scheduleText.replace("\n", " "));
        if (!matcher.find()) return;
        c.weeks = matcher.group(1).trim();
        c.weekday = matcher.group(2).trim();
        c.periods = matcher.group(3).trim();
        c.location = matcher.group(4).trim().replaceFirst("^★\\s*", "");
        c.time = periodTimeRange(c.periods);
    }

    private static String periodTimeRange(String periods) {
        List<String> values = new ArrayList<>();
        String[] chunks = periods.replace("节", "").split(",");
        for (String chunk : chunks) {
            String part = chunk.trim();
            if (part.isEmpty()) continue;
            try {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = range.length > 1 ? Integer.parseInt(range[1].trim()) : start;
                String[] s = PERIOD_TIMES.get(start);
                String[] e = PERIOD_TIMES.get(end);
                if (s != null && e != null) values.add(s[0] + "-" + e[1]);
            } catch (Exception ignored) {
            }
        }
        return String.join("、", values);
    }
}
