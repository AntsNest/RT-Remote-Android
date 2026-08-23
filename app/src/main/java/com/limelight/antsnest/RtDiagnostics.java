package com.limelight.antsnest;

import android.content.Context;
import android.os.Build;

import com.limelight.LimeLog;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;

/** Bounded, credential-free RT state trace uploaded through the existing diagnostics endpoint. */
public final class RtDiagnostics {
    private static final String ENDPOINT = "https://talk.antsnest.co.kr:4406/antsremote/crash";
    private static final int MAX_EVENTS = 120;
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();
    private static final String SESSION = UUID.randomUUID().toString().substring(0, 8);

    private RtDiagnostics() {}

    public static synchronized void record(String event, String detail) {
        String safe = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() > 500) safe = safe.substring(0, 500);
        String line = String.format(Locale.US, "%d session=%s thread=%s event=%s %s",
                System.currentTimeMillis(), SESSION, Thread.currentThread().getName(), event, safe);
        if (EVENTS.size() >= MAX_EVENTS) EVENTS.removeFirst();
        EVENTS.addLast(line);
        LimeLog.info("RTDIAG " + line);
    }

    public static void upload(Context context, String trigger) {
        final Context app = context.getApplicationContext();
        final String snapshot;
        synchronized (RtDiagnostics.class) {
            StringBuilder text = new StringBuilder("trigger=").append(trigger).append('\n');
            for (String event : EVENTS) text.append(event).append('\n');
            snapshot = text.toString();
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
                JSONObject json = new JSONObject();
                json.put("app", "rtremote-diag");
                json.put("version", version);
                json.put("device", Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE);
                json.put("happenedAt", System.currentTimeMillis());
                json.put("stack", snapshot);
                byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.getOutputStream().write(body);
                int status = connection.getResponseCode();
                LimeLog.info("RTDIAG upload trigger=" + trigger + " status=" + status);
            }
            catch (Exception error) {
                LimeLog.warning("RTDIAG upload failed trigger=" + trigger + " error=" + error);
            }
            finally {
                if (connection != null) connection.disconnect();
            }
        }, "rt-diagnostic-upload").start();
    }
}
