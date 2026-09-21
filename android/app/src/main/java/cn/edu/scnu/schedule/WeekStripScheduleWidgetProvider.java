package cn.edu.scnu.schedule;

public class WeekStripScheduleWidgetProvider extends ScheduleWidgetProvider {
    @Override
    protected int variant() {
        return ScheduleWidgetRenderer.VARIANT_WEEK_STRIP;
    }

    @Override
    protected int layoutId() {
        return R.layout.widget_schedule_week_strip;
    }
}
