package cn.edu.scnu.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int index = intent.getIntExtra("course_index", -1);
        int lead = intent.getIntExtra("lead_minutes", 15);
        List<Course> courses = AppPrefs.courses(context);
        if (index < 0 || index >= courses.size()) return;
        Course course = courses.get(index);
        NotificationHelper.showReminder(context, course, lead, AppPrefs.reminderType(context));
        ReminderScheduler.scheduleCourse(context, index, lead);
    }
}

