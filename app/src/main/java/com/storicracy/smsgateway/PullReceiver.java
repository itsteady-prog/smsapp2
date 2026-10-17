package com.storicracy.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The alarm lands here. It only hands over to the job. */
public class PullReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        if (new Prefs(app).enabled()) {
            Scheduler.runNow(app);
        }
        Scheduler.next(app);
    }
}
