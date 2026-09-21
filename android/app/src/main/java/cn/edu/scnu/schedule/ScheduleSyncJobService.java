package cn.edu.scnu.schedule;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.webkit.WebView;

import java.util.List;

public class ScheduleSyncJobService extends JobService {
    private WebView webView;
    private CourseSyncEngine engine;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!AppPrefs.isLoggedIn(this)) {
            jobFinished(params, false);
            return false;
        }
        webView = new WebView(this);
        engine = new CourseSyncEngine(this, webView, new CourseSyncEngine.Callback() {
            @Override
            public void onStatus(String message) {
            }

            @Override
            public void onSuccess(String semester, List<Course> courses) {
                AppPrefs.saveCourses(ScheduleSyncJobService.this, semester, courses);
                ScheduleWidgetProvider.updateAll(ScheduleSyncJobService.this);
                ReminderScheduler.scheduleAll(ScheduleSyncJobService.this);
                NotificationHelper.showSync(ScheduleSyncJobService.this, "课表已自动更新", "共读取到 " + courses.size() + " 门课程");
                cleanup();
                jobFinished(params, false);
            }

            @Override
            public void onError(String message) {
                NotificationHelper.showSync(ScheduleSyncJobService.this, "课表自动更新失败", message + " 请打开应用手动更新。");
                cleanup();
                jobFinished(params, false);
            }
        });
        engine.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        cleanup();
        return true;
    }

    private void cleanup() {
        if (engine != null) engine.stop();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        engine = null;
        webView = null;
    }
}

