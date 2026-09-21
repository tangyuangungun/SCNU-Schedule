package cn.edu.scnu.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;

public abstract class ScheduleWidgetProvider extends AppWidgetProvider {
    protected abstract int variant();
    protected abstract int layoutId();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, ScheduleWidgetRenderer.create(context, variant(), layoutId()));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        manager.updateAppWidget(appWidgetId, ScheduleWidgetRenderer.create(context, variant(), layoutId()));
    }

    @Override
    public void onEnabled(Context context) {
        updateAll(context);
    }

    public static void updateAll(Context context) {
        updateProvider(context, TodayScheduleWidgetProvider.class,
                ScheduleWidgetRenderer.VARIANT_TODAY, R.layout.widget_schedule_today);
        updateProvider(context, WeekStripScheduleWidgetProvider.class,
                ScheduleWidgetRenderer.VARIANT_WEEK_STRIP, R.layout.widget_schedule_week_strip);
        updateProvider(context, WeekGridScheduleWidgetProvider.class,
                ScheduleWidgetRenderer.VARIANT_WEEK_GRID, R.layout.widget_schedule_week_grid);
    }

    private static void updateProvider(Context context, Class<?> providerClass, int variant, int layoutId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, providerClass);
        int[] ids = manager.getAppWidgetIds(provider);
        for (int id : ids) {
            manager.updateAppWidget(id, ScheduleWidgetRenderer.create(context, variant, layoutId));
        }
    }

    protected static PendingIntent launchIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }
}
