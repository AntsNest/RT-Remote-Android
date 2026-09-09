package kr.co.antsnest.rtremote.antsnest;

import android.content.Context;
import android.os.Build;

import kr.co.antsnest.rtremote.LimeLog;

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
        String safe = detail == null ? "" : DiagnosticOutbox.redact(detail).replace('\n', ' ').replace('\r', ' ');
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
            StringBuilder text = new StringBuilder();
            for (String event : EVENTS) text.append(event).append('\n');
            // Preserve the newest failure instead of truncating it behind older progress events.
            snapshot = text.substring(Math.max(0, text.length() - 7400));
        }
        DiagnosticDelivery.enqueue(app, trigger, snapshot);
    }
}
