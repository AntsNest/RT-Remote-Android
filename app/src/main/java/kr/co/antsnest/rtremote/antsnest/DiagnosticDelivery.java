package kr.co.antsnest.rtremote.antsnest;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import kr.co.antsnest.rtremote.RtLog;
import org.json.JSONObject;
import java.io.File;

public final class DiagnosticDelivery {
    private static final int JOB_ID = 0x525444;
    private DiagnosticDelivery() {}
    static DiagnosticOutbox outbox(Context context) {
        return new DiagnosticOutbox(new File(context.getFilesDir(), "diagnostic-outbox"));
    }
    public static void save(Context context, String app, String version, String stack, Long happenedAt) throws Exception {
        JSONObject json = new JSONObject();
        json.put("app", app);
        json.put("version", version);
        json.put("device", Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE);
        json.put("happenedAt", happenedAt == null ? JSONObject.NULL : happenedAt);
        String safe = DiagnosticOutbox.redact(stack);
        json.put("stack", safe.substring(0, Math.min(safe.length(), 8000)));
        outbox(context).add(json.toString());
    }
    public static void enqueue(Context context, String trigger, String snapshot) {
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            save(context, "rtremote-diag", version, "trigger=" + trigger + "\n" + snapshot, System.currentTimeMillis());
            resume(context);
        } catch (Exception error) { RtLog.warning("RTDIAG could not queue diagnostic"); }
    }
    public static void resume(Context context) {
        if (outbox(context).pending().isEmpty()) return;
        try {
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, DiagnosticUploadService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build();
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null || scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS)
                RtLog.warning("RTDIAG upload not scheduled; report retained");
        } catch (Exception error) { RtLog.warning("RTDIAG scheduling failed; report retained"); }
    }
}
