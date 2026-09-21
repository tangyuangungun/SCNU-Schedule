package cn.edu.scnu.schedule;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Course {
    public String code = "";
    public String name = "";
    public String className = "";
    public String hours = "";
    public String credits = "";
    public String campus = "";
    public String department = "";
    public String teacher = "";
    public String firstClassDate = "";
    public String weeks = "";
    public String weekday = "";
    public String periods = "";
    public String time = "";
    public String location = "";
    public String scheduleText = "";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("code", code);
        o.put("name", name);
        o.put("className", className);
        o.put("hours", hours);
        o.put("credits", credits);
        o.put("campus", campus);
        o.put("department", department);
        o.put("teacher", teacher);
        o.put("firstClassDate", firstClassDate);
        o.put("weeks", weeks);
        o.put("weekday", weekday);
        o.put("periods", periods);
        o.put("time", time);
        o.put("location", location);
        o.put("scheduleText", scheduleText);
        return o;
    }

    public static Course fromJson(JSONObject o) {
        Course c = new Course();
        c.code = o.optString("code");
        c.name = o.optString("name");
        c.className = o.optString("className");
        c.hours = o.optString("hours");
        c.credits = o.optString("credits");
        c.campus = o.optString("campus");
        c.department = o.optString("department");
        c.teacher = o.optString("teacher");
        c.firstClassDate = o.optString("firstClassDate");
        c.weeks = o.optString("weeks");
        c.weekday = o.optString("weekday");
        c.periods = o.optString("periods");
        c.time = o.optString("time");
        c.location = o.optString("location");
        c.scheduleText = o.optString("scheduleText");
        return c;
    }

    public int periodStart() {
        int dash = periods.indexOf('-');
        try {
            if (dash > 0) return Integer.parseInt(periods.substring(0, dash).replaceAll("[^0-9]", ""));
            return Integer.parseInt(periods.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
            return 99;
        }
    }

    public int periodEnd() {
        try {
            String[] parts = periods.replace("节", "").split("[,，、]");
            int max = 0;
            for (String part : parts) {
                String[] range = part.trim().split("-");
                int value = Integer.parseInt(range[range.length - 1].trim());
                max = Math.max(max, value);
            }
            return max == 0 ? 99 : max;
        } catch (Exception ignored) {
            return 99;
        }
    }

    public int weekCount() {
        List<int[]> ranges = weekRanges();
        int count = 0;
        for (int[] range : ranges) count += Math.max(0, range[1] - range[0] + 1);
        return Math.max(1, count);
    }

    public boolean occursInWeek(int week) {
        List<int[]> ranges = weekRanges();
        if (ranges.isEmpty()) return true;
        for (int[] range : ranges) {
            if (week >= range[0] && week <= range[1]) return true;
        }
        return false;
    }

    private List<int[]> weekRanges() {
        List<int[]> result = new ArrayList<>();
        if (weeks == null || weeks.trim().isEmpty()) return result;
        String clean = weeks.replace("周", "")
                .replace("第", "")
                .replace("（", "(")
                .replace("）", ")");
        String rangeText = clean;
        int oddMarker = rangeText.indexOf('(');
        if (oddMarker >= 0) rangeText = rangeText.substring(0, oddMarker);
        boolean oddOnly = clean.contains("单");
        boolean evenOnly = clean.contains("双");
        String[] chunks = rangeText.split("[,，、;；\\s]+");
        for (String chunk : chunks) {
            if (chunk.isEmpty()) continue;
            try {
                String normalized = chunk.replace('~', '-').replace('至', '-');
                String[] range = normalized.split("-");
                int start = Integer.parseInt(range[0].replaceAll("[^0-9]", ""));
                int end = range.length > 1
                        ? Integer.parseInt(range[1].replaceAll("[^0-9]", ""))
                        : start;
                if (start <= 0 || end < start) continue;
                for (int week = start; week <= end; week++) {
                    if (oddOnly && week % 2 == 0) continue;
                    if (evenOnly && week % 2 != 0) continue;
                    result.add(new int[]{week, week});
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public Date firstDate() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(firstClassDate);
        } catch (Exception ignored) {
            return null;
        }
    }

    public int weekdayNumber() {
        switch (weekday) {
            case "星期一": return 1;
            case "星期二": return 2;
            case "星期三": return 3;
            case "星期四": return 4;
            case "星期五": return 5;
            case "星期六": return 6;
            case "星期日":
            case "星期天": return 7;
            default: return 99;
        }
    }

    public String compactTime() {
        return (weekday == null ? "" : weekday) + "  " + (time == null ? "" : time);
    }
}
