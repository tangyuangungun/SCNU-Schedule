package cn.edu.scnu.schedule;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public final class NotificationHelper {
    public static final String CHANNEL_REMINDERS = "class_reminders";
    public static final String CHANNEL_ALARM = "class_reminders_alarm";
    public static final String CHANNEL_MESSAGE = "class_reminders_message";
    public static final String CHANNEL_SYNC = "schedule_sync";

    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationChannel alarm = new NotificationChannel(
                CHANNEL_ALARM, "上课闹钟", NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("用闹钟铃声和振动提醒即将上课");
        AudioAttributes alarmAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        alarm.setSound(alarmSound, alarmAttributes);
        alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 800});
        manager.createNotificationChannel(alarm);

        NotificationChannel message = new NotificationChannel(
                CHANNEL_MESSAGE, "上课消息", NotificationManager.IMPORTANCE_DEFAULT);
        message.setDescription("用普通消息通知提醒即将上课");
        AudioAttributes messageAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        message.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), messageAttributes);
        message.enableVibration(true);
        manager.createNotificationChannel(message);

        NotificationChannel sync = new NotificationChannel(
                CHANNEL_SYNC, "课表更新", NotificationManager.IMPORTANCE_DEFAULT);
        sync.setDescription("自动更新或更新失败提示");
        manager.createNotificationChannel(sync);
    }

    public static void showReminder(Context context, Course course, int minutes) {
        showReminder(context, course, minutes, AppPrefs.reminderType(context));
    }

    public static void showReminder(Context context, Course course, int minutes, String reminderType) {
        ensureChannels(context);
        boolean alarm = AppPrefs.REMINDER_TYPE_ALARM.equals(reminderType);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, course.hashCode() + minutes, intent, flags);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, alarm ? CHANNEL_ALARM : CHANNEL_MESSAGE)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle((minutes > 0 ? minutes + "分钟后上课：" : "即将上课：") + course.name)
                .setContentText(course.time + " · " + course.location)
                .setStyle(new android.app.Notification.BigTextStyle()
                        .bigText(course.name + "\n" + course.time + "\n" + course.location))
                .setContentIntent(pendingIntent)
                .setCategory(alarm ? android.app.Notification.CATEGORY_ALARM : android.app.Notification.CATEGORY_EVENT)
                .setPriority(alarm ? android.app.Notification.PRIORITY_MAX : android.app.Notification.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Uri sound = alarm
                    ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            builder.setSound(sound);
            builder.setVibrate(alarm ? new long[]{0, 500, 250, 500, 250, 800} : new long[]{0, 180, 120, 180});
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((alarm ? 100000 : 200000) + course.hashCode() + minutes, builder.build());
    }

    public static void showSync(Context context, String title, String message) {
        ensureChannels(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 9911, intent, flags);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_SYNC)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(9911, builder.build());
    }
}

