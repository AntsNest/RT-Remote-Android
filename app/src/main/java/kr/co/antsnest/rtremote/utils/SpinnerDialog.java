package kr.co.antsnest.rtremote.utils;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SpinnerDialog implements Runnable,OnCancelListener {
    private final String title;
    private final String message;
    private final Activity activity;
    private Dialog progress;
    private TextView messageView;
    private final boolean finish;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.progress = null;
        this.finish = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish)
    {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity)
    {
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> i = rundownDialogs.iterator();
            while (i.hasNext()) {
                SpinnerDialog dialog = i.next();
                if (dialog.activity == activity) {
                    i.remove();
                    if (dialog.progress.isShowing()) {
                        dialog.progress.dismiss();
                    }
                }
            }
        }
    }

    public void dismiss()
    {
        // Running again with progress != null will destroy it
        activity.runOnUiThread(this);
    }

    public void setMessage(final String message)
    {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageView != null) {
                    messageView.setText(friendlyStage(message));
                }
            }
        });
    }

    @Override
    public void run() {

        // If we're dying, don't bother doing anything
        if (activity.isFinishing()) {
            return;
        }

        if (progress == null)
        {
            progress = new Dialog(activity);
            progress.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int side = dp(28);
            card.setPadding(side, dp(24), side, dp(22));

            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(24, 32, 47));
            background.setCornerRadius(dp(22));
            background.setStroke(dp(1), Color.rgb(52, 68, 91));
            card.setBackground(background);

            ProgressBar indicator = new ProgressBar(activity);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                indicator.getIndeterminateDrawable().setTint(Color.rgb(38, 198, 181));
            }
            card.addView(indicator, new LinearLayout.LayoutParams(dp(46), dp(46)));

            TextView titleView = new TextView(activity);
            titleView.setText(title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(19);
            titleView.setGravity(Gravity.CENTER);
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(15);
            card.addView(titleView, titleParams);

            messageView = new TextView(activity);
            messageView.setText(friendlyStage(message));
            messageView.setTextColor(Color.rgb(184, 197, 218));
            messageView.setTextSize(14);
            messageView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = dp(8);
            card.addView(messageView, messageParams);

            progress.setContentView(card, new ViewGroup.LayoutParams(dp(310), ViewGroup.LayoutParams.WRAP_CONTENT));
            progress.setOnCancelListener(this);

            // If we want to finish the activity when this is killed, make it cancellable
            if (finish)
            {
                progress.setCancelable(true);
                progress.setCanceledOnTouchOutside(false);
            }
            else
            {
                progress.setCancelable(false);
            }

            synchronized (rundownDialogs) {
                rundownDialogs.add(this);
                progress.show();
                Window window = progress.getWindow();
                if (window != null) {
                    window.setBackgroundDrawableResource(android.R.color.transparent);
                    window.setDimAmount(0.55f);
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            }
        }
        else
        {
            synchronized (rundownDialogs) {
                if (rundownDialogs.remove(this) && progress.isShowing()) {
                    progress.dismiss();
                }
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** 네트워크 내부 단계명은 사용자에게 이해되는 진행 문구로 바꾼다. */
    private static String friendlyStage(String value) {
        if (value != null && value.startsWith("[화면 전환]")) return value;
        if (value == null) return "연결을 준비하고 있습니다";
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("handshake") || lower.contains("rtsp")) return "보안 연결을 확인하고 있습니다";
        if (lower.contains("video") || lower.contains("decoder")) return "화면을 준비하고 있습니다";
        if (lower.contains("audio")) return "소리를 준비하고 있습니다";
        if (lower.contains("control") || lower.contains("input")) return "원격 제어를 준비하고 있습니다";
        if (lower.contains("connection") || lower.contains("connect")) return "PC와 연결하고 있습니다";
        return "원격 화면을 준비하고 있습니다";
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }

        // This will only be called if finish was true, so we don't need to check again
        activity.finish();
    }
}
