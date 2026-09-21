package cn.edu.scnu.schedule;

public class WeekGridScheduleWidgetProvider extends ScheduleWidgetProvider {
    @Override
    protected int variant() {
        return ScheduleWidgetRenderer.VARIANT_WEEK_GRID;
    }

    @Override
    protected int layoutId() {
        return R.layout.widget_schedule_week_grid;
    }
}
