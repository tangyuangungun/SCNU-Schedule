package cn.edu.scnu.schedule;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class UpdateCheckScheduler {
    private static final int JOB_ID = 224032;
    private static final long DAY_MS = 48L * 60L * 60L * 1000L;

    private UpdateCheckScheduler() {}

    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        scheduler.cancel(JOB_ID);
        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, UpdateCheckJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(DAY_MS, 4L * 60L * 60L * 1000L)
                .build();
        scheduler.schedule(job);
    }
}

