package kr.co.antsnest.rtremote;

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
            RtLog.setFileHandler(new File(getFilesDir(), "rtremote-%g.log").getAbsolutePath());
            RtLog.info("RTDIAG app_process_start sdk=" + Build.VERSION.SDK_INT);
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

        kr.co.antsnest.rtremote.antsnest.DiagnosticDelivery.save(ctx, "rtremote", version, text, System.currentTimeMillis());
        Log.e(TAG, "Crash recorded: " + error.getClass().getSimpleName());
    }

    /** Migrate legacy evidence before deleting it, then resume network-constrained uploads. */
    private void send(Context ctx) {
        new Thread(() -> {
            try {
                File file = new File(ctx.getFilesDir(), FILE);
                if (file.exists()) {
                    String body = kr.co.antsnest.rtremote.antsnest.DiagnosticOutbox.read(file);
                    if (!body.trim().isEmpty()) {
                        String at = group(body, "^at=(\\d+)$");
                        kr.co.antsnest.rtremote.antsnest.DiagnosticDelivery.save(ctx, "rtremote", version, body,
                                at.isEmpty() ? null : Long.parseLong(at));
                    }
                    file.delete();
                }
            } catch (Exception error) { Log.w(TAG, "Legacy crash retained for next startup"); }
            kr.co.antsnest.rtremote.antsnest.DiagnosticDelivery.resume(ctx);
        }, "rt-diagnostic-resume").start();
    }

    private static String group(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        return m.find() ? m.group(1) : "";
    }
}
