package cn.edu.scnu.schedule;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageInfo;

public class UpdateCheckJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        if (!AppPrefs.privacyAccepted(this)) {
            jobFinished(params, false);
            return false;
        }
        String version;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {
            version = "";
        }
        UpdateChecker.check(this, true, version, new UpdateChecker.Callback() {
            @Override
            public void onResult(boolean updateAvailable, String latestVersion, String url, String notes) {
                jobFinished(params, false);
            }

            @Override
            public void onError(String message) {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}

