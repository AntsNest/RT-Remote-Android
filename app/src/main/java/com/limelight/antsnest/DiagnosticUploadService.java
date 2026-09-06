package com.limelight.antsnest;

import android.app.job.JobParameters;
import android.app.job.JobService;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Network-constrained job persisted across process death and device restart. */
public final class DiagnosticUploadService extends JobService {
    private AtomicBoolean active = new AtomicBoolean(false);

    @Override public boolean onStartJob(JobParameters parameters) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        active = cancelled;
        new Thread(() -> {
            DiagnosticOutbox outbox = DiagnosticDelivery.outbox(this);
            boolean retry = false;
            try {
                for (String name : outbox.pending()) {
                    if (cancelled.get()) return;
                    if (!outbox.deliver(name, DiagnosticUploadService::upload)) { retry = true; break; }
                }
            } catch (Exception error) { retry = true; }
            if (!cancelled.get()) jobFinished(parameters, retry || !outbox.pending().isEmpty());
        }, "rt-diagnostic-delivery").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        active.set(true);
        return true;
    }

    private static boolean upload(String payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://talk.antsnest.co.kr:4406/antsremote/crash").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload.getBytes(StandardCharsets.UTF_8)); }
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return false;
            StringBuilder body = new StringBuilder();
            try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) body.append(line);
            }
            return new JSONObject(body.toString()).optBoolean("success", false);
        } finally { connection.disconnect(); }
    }
}
