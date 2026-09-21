package cn.edu.scnu.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AppPrefs.isLoggedIn(context)) return;
        NotificationHelper.ensureChannels(context);
        SyncScheduler.schedule(context, AppPrefs.updateFrequency(context));
        ReminderScheduler.scheduleAll(context);
        ScheduleWidgetProvider.updateAll(context);
    }
}

