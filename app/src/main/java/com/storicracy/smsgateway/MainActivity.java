package com.storicracy.smsgateway;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/** One screen: the Office address, the token, a switch, and a few buttons. Built in code so there are no layout files to carry. */
public class MainActivity extends Activity {
    private Prefs prefs;
    private EditText server;
    private EditText token;
    private EditText interval;
    private Switch enabled;
    private Switch manual;
    private TextView status;
    private JSONObject handling;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        int pad = dp(16);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Storicracy SMS");
        title.setTextSize(24);
        col.addView(title);
        TextView intro = new TextView(this);
        intro.setText("This phone collects waiting texts from the Office and sends them from its own SIM. Keep it charged, switched on and online.");
        intro.setPadding(0, dp(8), 0, dp(16));
        col.addView(intro);

        button(col, "Scan code to link", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scan();
            }
        });
        TextView hint = new TextView(this);
        hint.setText("In the Office, open Settings, Text messages, and choose \"Link a phone by scanning a code\". Then scan the code here. You can also fill in the address and token by hand below.");
        hint.setPadding(0, dp(4), 0, dp(8));
        col.addView(hint);

        String address = prefs.server();
        if (address.isEmpty()) {
            address = fromAsset("server.txt");
        }
        server = field(col, "Office address (starts with https://)", address, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        token = field(col, "Token from the Office", prefs.token(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        interval = field(col, "Check every (minutes)", String.valueOf(prefs.interval()), InputType.TYPE_CLASS_NUMBER);

        enabled = new Switch(this);
        enabled.setText("Gateway is on");
        enabled.setChecked(prefs.enabled());
        col.addView(enabled);
        manual = new Switch(this);
        manual.setText("Send by hand in my SMS app (asks me for each text)");
        manual.setChecked(prefs.manual());
        col.addView(manual);

        button(col, "Save", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        button(col, "Allow sending SMS", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 1);
            }
        });
        button(col, "Check the connection", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkConnection();
            }
        });
        button(col, "Check for texts now", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                Scheduler.runNow(getApplicationContext());
                Toast.makeText(MainActivity.this, "Checking now.", Toast.LENGTH_SHORT).show();
            }
        });
        button(col, "Send the next waiting text by hand", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byHand();
            }
        });

        status = new TextView(this);
        status.setPadding(0, dp(16), 0, 0);
        col.addView(status);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
        handleLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLink(intent);
    }

    /** Opens the camera inside the app to read the code shown in the Office. */
    private void scan() {
        IntentIntegrator ii = new IntentIntegrator(this);
        ii.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        ii.setPrompt("Point the camera at the code in the Office");
        ii.setBeepEnabled(false);
        ii.setOrientationLocked(false);
        ii.initiateScan();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        IntentResult r = IntentIntegrator.parseActivityResult(request, result, data);
        if (r != null) {
            if (r.getContents() != null) {
                link(r.getContents());
            }
            return;
        }
        super.onActivityResult(request, result, data);
    }

    /** A code opened from the phone's own camera arrives as a storicracy-sms:// link. */
    private void handleLink(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        Uri u = intent.getData();
        if ("storicracy-sms".equals(u.getScheme())) {
            intent.setData(null);
            link(u.toString());
        }
    }

    /** Reads storicracy-sms://link?s=ADDRESS&c=CODE, trades the code for a token, and saves both. */
    private void link(String text) {
        final String server;
        final String code;
        try {
            Uri u = Uri.parse(text.trim());
            if (!"storicracy-sms".equals(u.getScheme())) {
                throw new IllegalArgumentException();
            }
            server = u.getQueryParameter("s");
            code = u.getQueryParameter("c");
            if (server == null || code == null || !server.startsWith("https://") || !code.matches("[a-f0-9]{24}")) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            Toast.makeText(this, "That is not a code from the Office.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Linking...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    String[] r = Gateway.pair(server, code);
                    prefs.save(server, r[0], true, prefs.manual(), Math.max(1, Math.min(60, Integer.parseInt(r[2]))));
                    message = "Linked as " + r[1] + ".";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            server_field().setText(server);
                            token.setText(prefs.token());
                            enabled.setChecked(true);
                            interval.setText(String.valueOf(prefs.interval()));
                            Scheduler.next(getApplicationContext());
                            refresh();
                            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 1);
                            }
                        }
                    });
                } catch (Exception e) {
                    message = "Could not link: " + e.getMessage();
                }
                final String text = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private EditText server_field() {
        return server;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (handling != null) {
            askHowItWent();
        }
    }

    private void refresh() {
        int waiting = prefs.pending().length();
        status.setText(prefs.status() + (waiting > 0 ? "\n" + waiting + " waiting to send by hand." : "")
                + (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED ? "" : "\nSMS permission has not been given yet."));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private EditText field(LinearLayout col, String label, String value, int type) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setPadding(0, dp(8), 0, 0);
        col.addView(l);
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setText(value);
        e.setSingleLine(true);
        col.addView(e);
        return e;
    }

    private void button(LinearLayout col, String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(click);
        col.addView(b);
    }

    private String fromAsset(String name) {
        try {
            InputStream in = getAssets().open(name);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = r.readLine();
            r.close();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void save() {
        String s = server.getText().toString().trim();
        if (!s.isEmpty() && !s.startsWith("https://")) {
            Toast.makeText(this, "The address must start with https://", Toast.LENGTH_LONG).show();
            return;
        }
        int every = 5;
        try {
            every = Math.max(1, Math.min(60, Integer.parseInt(interval.getText().toString().trim())));
        } catch (Exception ignored) {
            every = 5;
        }
        prefs.save(s, token.getText().toString().trim(), enabled.isChecked(), manual.isChecked(), every);
        Scheduler.next(getApplicationContext());
        Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void checkConnection() {
        save();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    result = Gateway.ping(prefs);
                } catch (Exception e) {
                    result = "Problem: " + e.getMessage();
                }
                final String text = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    /** Opens the phone's own SMS app with the number and text filled in. The person taps Send. */
    private void byHand() {
        JSONArray list = prefs.pending();
        if (list.length() == 0) {
            Toast.makeText(this, "Nothing is waiting. Use \"Check for texts now\" first, with \"send by hand\" switched on.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            handling = list.getJSONObject(0);
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(handling.getString("to"))));
            i.putExtra("sms_body", handling.getString("body"));
            startActivity(i);
        } catch (Exception e) {
            handling = null;
            Toast.makeText(this, "That text could not be opened.", Toast.LENGTH_LONG).show();
        }
    }

    private void askHowItWent() {
        final JSONObject m = handling;
        handling = null;
        new AlertDialog.Builder(this)
                .setTitle("Did you send it?")
                .setMessage("Tell the Office whether this text was sent.")
                .setCancelable(false)
                .setPositiveButton("Yes, sent", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finishOne(m, true);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finishOne(m, false);
                    }
                })
                .show();
    }

    private void finishOne(final JSONObject m, final boolean ok) {
        JSONArray list = prefs.pending();
        JSONArray rest = new JSONArray();
        long id = m.optLong("id");
        for (int i = 0; i < list.length(); i++) {
            JSONObject x = list.optJSONObject(i);
            if (x != null && x.optLong("id") != id) {
                rest.put(x);
            }
        }
        prefs.pending(rest);
        refresh();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject one = new JSONObject();
                    one.put("id", m.optLong("id"));
                    one.put("ok", ok);
                    if (!ok) {
                        one.put("error", "Not sent by hand");
                    }
                    Gateway.report(prefs, new JSONArray().put(one));
                } catch (Exception ignored) {
                    // The Office will put the text back in the queue if it hears nothing.
                }
            }
        }).start();
    }
}
