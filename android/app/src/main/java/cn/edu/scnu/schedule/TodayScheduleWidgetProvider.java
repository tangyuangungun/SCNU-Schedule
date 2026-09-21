package cn.edu.scnu.schedule;

public class TodayScheduleWidgetProvider extends ScheduleWidgetProvider {
    @Override
    protected int variant() {
        return ScheduleWidgetRenderer.VARIANT_TODAY;
    }

    @Override
    protected int layoutId() {
        return R.layout.widget_schedule_today;
    }
}
