package com.storicracy.smsgateway;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * An inexact alarm wakes the app every few minutes. The alarm starts a one-off job, and the job does the work.
 * Android may delay the alarm a little when the phone is asleep. That saves battery and is fine for notifications.
 */
final class Scheduler {
    static final int JOB_ID = 4201;

    private Scheduler() { }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, PullReceiver.class);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Sets the next alarm, or cancels it when the gateway is switched off. */
    static void next(Context c) {
        Prefs p = new Prefs(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c);
        if (!p.enabled()) {
            am.cancel(pi);
            return;
        }
        long at = System.currentTimeMillis() + Math.max(1, p.interval()) * 60000L;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    /** Runs a check straight away, in a job, so a long send is not cut short. */
    static void runNow(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, PullJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0)
                .build();
        js.schedule(job);
    }
}
