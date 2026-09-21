package cn.edu.scnu.schedule;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

public final class SyncScheduler {
    public static final int JOB_ID = 224031;
    private static final long DAY = 24L * 60L * 60L * 1000L;

    private SyncScheduler() {}

    public static void schedule(Context context, String frequency) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        scheduler.cancel(JOB_ID);
        if (AppPrefs.FREQ_MANUAL.equals(frequency)) return;

        long interval = AppPrefs.FREQ_MONTHLY.equals(frequency) ? 30L * DAY : 7L * DAY;
        long flex = Math.max(60L * 60L * 1000L, interval / 10L);
        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, ScheduleSyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(interval, flex)
                .build();
        scheduler.schedule(job);
    }
}
