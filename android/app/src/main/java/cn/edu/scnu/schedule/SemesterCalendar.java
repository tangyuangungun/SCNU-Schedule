package cn.edu.scnu.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Academic calendar rules used by the app. */
public final class SemesterCalendar {
    private SemesterCalendar() {}

    public static final class Semester {
        public final int startYear;
        public final boolean autumn;
        public final LocalDate startDate;
        public final LocalDate endExclusive;
        public final LocalDate weekOneMonday;
        public final int totalWeeks;

        Semester(int startYear, boolean autumn, LocalDate startDate, LocalDate endExclusive) {
            this.startYear = startYear;
            this.autumn = autumn;
            this.startDate = startDate;
            this.endExclusive = endExclusive;
            this.weekOneMonday = startDate.with(DayOfWeek.MONDAY);
            this.totalWeeks = Math.max(1, (int) (ChronoUnit.DAYS.between(weekOneMonday, endExclusive) / 7));
        }

        public String key() {
            return startYear + (autumn ? "-autumn" : "-spring");
        }

        public String title() {
            if (autumn) {
                return startYear + "-" + (startYear + 1) + " 学年 第一学期";
            }
            return (startYear - 1) + "-" + startYear + " 学年 第二学期";
        }

        public int weekFor(LocalDate date) {
            long raw = ChronoUnit.WEEKS.between(weekOneMonday, date.with(DayOfWeek.MONDAY)) + 1;
            if (raw < 1) return 1;
            if (raw > totalWeeks) return totalWeeks;
            return (int) raw;
        }

        public LocalDate weekStart(int week) {
            return weekOneMonday.plusWeeks(Math.max(1, Math.min(totalWeeks, week)) - 1L);
        }

        public LocalDate weekEnd(int week) {
            return weekStart(week).plusDays(6);
        }

        public boolean isInTerm(LocalDate date) {
            return !date.isBefore(startDate) && date.isBefore(endExclusive);
        }

        public String relationship(LocalDate date) {
            if (date.isBefore(startDate)) {
                long days = ChronoUnit.DAYS.between(date, startDate);
                return days == 0 ? "今天开课" : "距开课 " + days + " 天";
            }
            if (!date.isBefore(endExclusive)) return "假期中";
            return weekFor(date) == weekFor(LocalDate.now()) ? "本周" : "非本周";
        }
    }

    public static Semester resolve(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 9) {
            int year = date.getYear();
            return new Semester(year, true, LocalDate.of(year, 9, 7), LocalDate.of(year + 1, 1, 25));
        }
        if (month == 1) {
            int year = date.getYear() - 1;
            return new Semester(year, true, LocalDate.of(year, 9, 7), LocalDate.of(year + 1, 1, 25));
        }
        int year = date.getYear();
        return new Semester(year, false, LocalDate.of(year, 2, 22), LocalDate.of(year, 7, 12));
    }
}
