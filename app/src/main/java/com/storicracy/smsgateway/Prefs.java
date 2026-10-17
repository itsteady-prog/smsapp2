package com.storicracy.smsgateway;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Everything the app remembers: the Office address, the token, the schedule, and the last result. */
final class Prefs {
    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences("gateway", Context.MODE_PRIVATE);
    }

    String server() { return p.getString("server", ""); }
    String token() { return p.getString("token", ""); }
    boolean enabled() { return p.getBoolean("enabled", false); }
    boolean manual() { return p.getBoolean("manual", false); }
    int interval() { return p.getInt("interval", 5); }
    String status() { return p.getString("status", "Not started yet."); }

    void save(String server, String token, boolean enabled, boolean manual, int interval) {
        p.edit().putString("server", server).putString("token", token).putBoolean("enabled", enabled)
                .putBoolean("manual", manual).putInt("interval", interval).apply();
    }

    void interval(int minutes) { p.edit().putInt("interval", minutes).apply(); }

    void status(String text) {
        String stamp = new SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(new Date());
        p.edit().putString("status", stamp + "  " + text).apply();
    }

    /** Messages fetched in "send by hand" mode that are still waiting for a person to send them. */
    JSONArray pending() {
        try {
            return new JSONArray(p.getString("pending", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    void pending(JSONArray list) { p.edit().putString("pending", list.toString()).apply(); }
}
