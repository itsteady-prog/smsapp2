package com.storicracy.smsgateway;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

/** Asks the Office for waiting texts, sends them, and reports back. Runs off the main thread. */
public class PullJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    result = Gateway.run(app);
                } catch (Throwable t) {
                    result = "Problem: " + t.getMessage();
                }
                new Prefs(app).status(result);
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
