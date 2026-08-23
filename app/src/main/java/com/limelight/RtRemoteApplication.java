package com.limelight;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 앱이 죽은 자리를 남겨 다음에 켜질 때 앤츠네스트 서버로 올려보낸다.
 *
 * 이 앱은 앤츠톡과 별개로 설치되고 우리 소켓도 없다. 그래서 사용자가 원격지에
 * 있으면 "앱이 팅긴다" 는 말 말고는 아무것도 손에 들어오지 않는다 — logcat 을
 * 볼 수 없고, 앤츠톡의 진단 통로도 이 앱에는 닿지 않는다(2026-08-21).
 *
 * 죽는 순간에는 디스크에만 적는다. 그때 네트워크로 보내려 하면 대개 못 보낸다 —
 * 프로세스가 곧 사라지므로 소켓이 열릴 시간도 응답을 기다릴 시간도 없고,
 * 붙들다가 강제 종료를 앞당기면 그 기록마저 잃는다. 적는 건 죽을 때, 보내는
 * 건 다음에 살아났을 때로 떼어 놓는다.
 */
public class RtRemoteApplication extends Application {

    private static final String TAG = "RtRemoteCrash";
    private static final String FILE = "pending-crash.txt";
    private static final String URL_ENDPOINT =
            "https://talk.antsnest.co.kr:4406/antsremote/crash";

    private String version = "";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            LimeLog.setFileHandler(new File(getFilesDir(), "rtremote-%g.log").getAbsolutePath());
            LimeLog.info("RTDIAG app_process_start sdk=" + Build.VERSION.SDK_INT);
        }
        catch (IOException error) {
            Log.e(TAG, "RT diagnostic file logger initialization failed", error);
        }

        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { /* 버전을 몰라도 스택은 보내야 한다 */ }

        final Context ctx = getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                // 여기서 나는 예외는 아무도 못 받는다. 기록에 실패하더라도 원래
                // 핸들러(시스템 다이얼로그·종료)까지는 반드시 넘긴다.
                try {
                    write(ctx, thread, error);
                } catch (Throwable t) {
                    Log.e(TAG, "크래시 기록 실패", t);
                }
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            }
        });

        send(ctx);
    }

    private void write(Context ctx, Thread thread, Throwable error) throws Exception {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));

        String text = "thread=" + thread.getName() + "\n"
                + "device=" + Build.MANUFACTURER + " " + Build.MODEL
                + " (Android " + Build.VERSION.RELEASE + ")\n"
                + "app=rtremote v" + version + "\n"
                + "at=" + System.currentTimeMillis() + "\n"
                + sw;

        java.io.FileOutputStream out = new java.io.FileOutputStream(new File(ctx.getFilesDir(), FILE));
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        Log.e(TAG, "크래시 기록: " + error);
    }

    /**
     * 쌓인 기록을 보낸다 — 보냈든 못 보냈든 한 번 시도하고 지운다.
     *
     * 못 보냈다고 붙들고 있으면 다음 크래시가 그 자리를 못 쓴다. 가장 최근에
     * 죽은 이유를 잃는 편보다 한 번 놓치는 편이 낫다.
     */
    private void send(Context ctx) {
        final File file = new File(ctx.getFilesDir(), FILE);
        if (!file.exists()) return;

        String read = null;
        try {
            byte[] buf = new byte[(int) Math.min(file.length(), 64 * 1024)];
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                int n = in.read(buf);
                if (n > 0) read = new String(buf, 0, n, StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (Exception ignored) { /* 못 읽으면 보낼 것도 없다 */ }

        //noinspection ResultOfMethodCallIgnored
        file.delete();
        if (read == null || read.trim().isEmpty()) return;

        final String body = read;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONObject json = new JSONObject();
                    json.put("app", "rtremote");
                    json.put("version", version);
                    json.put("device", group(body, "^device=(.*)$"));
                    String at = group(body, "^at=(\\d+)$");
                    json.put("happenedAt", at.isEmpty() ? JSONObject.NULL : Long.parseLong(at));
                    json.put("stack", body);

                    conn = (HttpURLConnection) new URL(URL_ENDPOINT).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(json.toString().getBytes(StandardCharsets.UTF_8));
                    Log.i(TAG, "지난 크래시 전송: HTTP " + conn.getResponseCode());
                } catch (Exception e) {
                    Log.w(TAG, "지난 크래시 전송 실패: " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "crash-report");
        t.setDaemon(true);
        t.start();
    }

    private static String group(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        return m.find() ? m.group(1) : "";
    }
}
