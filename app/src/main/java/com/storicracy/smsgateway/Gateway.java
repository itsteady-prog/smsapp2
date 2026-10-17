package com.storicracy.smsgateway;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Talks to the Office and to the phone's SMS service. */
final class Gateway {
    static final String APP = "gateway-1.1";

    private Gateway() { }

    /** One request to the Office. The server address must be https. */
    static JSONObject call(Prefs p, String action, JSONObject body) throws Exception {
        String base = p.server().trim().replaceAll("/+$", "");
        URL url = new URL(base + "/" + action);
        if (!"https".equals(url.getProtocol())) {
            throw new IOException("The server address must start with https://");
        }
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-SKS-Token", p.token().trim());
            c.setRequestProperty("Authorization", "Bearer " + p.token().trim());
            byte[] data = body.toString().getBytes("UTF-8");
            c.setFixedLengthStreamingMode(data.length);
            OutputStream out = c.getOutputStream();
            try {
                out.write(data);
            } finally {
                out.close();
            }
            int code = c.getResponseCode();
            if (code == 401) {
                throw new IOException("The Office refused the token. Check it, or ask for a new one.");
            }
            if (code == 503) {
                throw new IOException("Text messages are switched off in the Office.");
            }
            if (code == 429) {
                throw new IOException("Too many tries. Wait a few minutes.");
            }
            if (code >= 400) {
                throw new IOException("The Office answered " + code + ".");
            }
            return new JSONObject(read(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    /** Trades the one-time code from the QR for this phone's own token. Nothing is saved here; the caller saves the result. */
    static String[] pair(String server, String code) throws Exception {
        String base = server.trim().replaceAll("/+$", "");
        URL url = new URL(base + "/pair");
        if (!"https".equals(url.getProtocol())) {
            throw new IOException("The server address must start with https://");
        }
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] data = new JSONObject().put("code", code).put("app", APP).toString().getBytes("UTF-8");
            c.setFixedLengthStreamingMode(data.length);
            OutputStream out = c.getOutputStream();
            try {
                out.write(data);
            } finally {
                out.close();
            }
            int status = c.getResponseCode();
            if (status == 401) {
                throw new IOException("That code is not valid or has expired. Make a new one in the Office.");
            }
            if (status == 503) {
                throw new IOException("Text messages are switched off in the Office.");
            }
            if (status == 429) {
                throw new IOException("Too many tries. Wait a few minutes.");
            }
            if (status >= 400) {
                throw new IOException("The Office answered " + status + ".");
            }
            JSONObject r = new JSONObject(read(c.getInputStream()));
            String token = r.optString("token", "");
            if (token.isEmpty()) {
                throw new IOException("The Office sent no token.");
            }
            return new String[]{token, r.optString("device", "this phone"), String.valueOf(r.optInt("interval", 5))};
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > 200000) {
                    throw new IOException("The answer was too large.");
                }
            }
        } finally {
            in.close();
        }
        return b.toString("UTF-8");
    }

    /** Checks the connection. */
    static String ping(Prefs p) throws Exception {
        JSONObject r = call(p, "ping", new JSONObject().put("app", APP));
        return "Connected to the Office as " + r.optString("device", "this phone") + ".";
    }

    /** One round: ask for waiting texts, then send them (or keep them for sending by hand). Returns a line for the status. */
    static String run(Context ctx) {
        Prefs p = new Prefs(ctx);
        if (!p.enabled()) {
            return "Switched off.";
        }
        if (p.server().trim().isEmpty() || p.token().trim().isEmpty()) {
            return "Not set up yet.";
        }
        try {
            JSONObject r = call(p, "pull", new JSONObject().put("app", APP));
            int every = r.optInt("interval", p.interval());
            if (every >= 1 && every <= 60 && every != p.interval()) {
                p.interval(every);
            }
            JSONArray list = r.optJSONArray("messages");
            int n = list == null ? 0 : list.length();
            if (n == 0) {
                return "Connected. Nothing was waiting.";
            }
            if (p.manual()) {
                JSONArray have = p.pending();
                for (int i = 0; i < n; i++) {
                    have.put(list.getJSONObject(i));
                }
                p.pending(have);
                return n + " text" + (n == 1 ? "" : "s") + " waiting for you to send by hand.";
            }
            JSONArray results = new JSONArray();
            int sent = 0;
            for (int i = 0; i < n; i++) {
                JSONObject m = list.getJSONObject(i);
                String err = sendOne(ctx, m.getLong("id"), m.getString("to"), m.getString("body"));
                JSONObject one = new JSONObject();
                one.put("id", m.getLong("id"));
                one.put("ok", err == null);
                if (err != null) {
                    one.put("error", err);
                } else {
                    sent++;
                }
                results.put(one);
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            report(p, results);
            return "Sent " + sent + " of " + n + ".";
        } catch (Exception e) {
            return "Problem: " + e.getMessage();
        }
    }

    /** Tells the Office how each text went. */
    static void report(Prefs p, JSONArray results) throws Exception {
        call(p, "report", new JSONObject().put("app", APP).put("results", results));
    }

    private static String reason(int code) {
        switch (code) {
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "No mobile signal";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "Airplane mode is on";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "The message was empty";
            default:
                return "The phone could not send it";
        }
    }

    /** Sends one text through the phone's SIM and waits for Android to say whether it left. Returns null when it did. */
    static String sendOne(Context ctx, long id, String to, String body) {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "The app has no permission to send SMS";
        }
        SmsManager sm = Build.VERSION.SDK_INT >= 31 ? ctx.getSystemService(SmsManager.class) : SmsManager.getDefault();
        if (sm == null) {
            return "This phone cannot send SMS";
        }
        ArrayList<String> parts = sm.divideMessage(body);
        final int total = parts.size();
        final CountDownLatch done = new CountDownLatch(total);
        final AtomicReference<String> failure = new AtomicReference<String>(null);
        String action = ctx.getPackageName() + ".SENT." + id;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (getResultCode() != Activity.RESULT_OK) {
                    failure.compareAndSet(null, reason(getResultCode()));
                }
                done.countDown();
            }
        };
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ctx.registerReceiver(receiver, filter);
        }
        try {
            ArrayList<PendingIntent> intents = new ArrayList<PendingIntent>();
            for (int i = 0; i < total; i++) {
                Intent in = new Intent(action).setPackage(ctx.getPackageName());
                int code = (int) (id % 100000) * 20 + i;
                intents.add(PendingIntent.getBroadcast(ctx, code, in, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            if (total == 1) {
                sm.sendTextMessage(to, null, body, intents.get(0), null);
            } else {
                sm.sendMultipartTextMessage(to, null, parts, intents, null);
            }
            boolean all = done.await(45, TimeUnit.SECONDS);
            if (!all) {
                return "No confirmation from the phone";
            }
            return failure.get();
        } catch (Exception e) {
            return "The phone could not send it";
        } finally {
            try {
                ctx.unregisterReceiver(receiver);
            } catch (Exception ignored) {
                // already gone
            }
        }
    }
}
