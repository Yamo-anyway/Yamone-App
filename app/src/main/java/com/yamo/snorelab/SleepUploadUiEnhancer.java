package com.yamo.snorelab;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/** Adds sleep upload controls without changing MainActivity's existing layout code. */
public final class SleepUploadUiEnhancer {
    private static final String UPLOAD_TAG = "yamone_sleep_upload_button_v1";
    private static final WeakHashMap<MainActivity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS = new WeakHashMap<>();

    private SleepUploadUiEnhancer() {}

    public static synchronized void attach(MainActivity activity) {
        if (activity == null || LISTENERS.containsKey(activity)) return;
        View decor = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> enhance(activity);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        LISTENERS.put(activity, listener);
        decor.post(() -> enhance(activity));
    }

    public static synchronized void detach(MainActivity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(activity);
        View decor = activity.getWindow().getDecorView();
        if (listener != null && decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private static void enhance(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        updatePrivacyCopy(root);

        TextView managementTitle = findExactText(root, "기록 관리");
        if (managementTitle == null || !(managementTitle.getParent() instanceof ViewGroup)) return;
        ViewGroup card = (ViewGroup) managementTitle.getParent();
        if (card.findViewWithTag(UPLOAD_TAG) != null) return;

        File sessionDir = currentDetailSession(activity);
        if (!isCompletedOwnedSession(activity, sessionDir)) return;

        JSONObject meta = SessionStore.readMeta(sessionDir);
        Button upload = buildUploadButton(activity, sessionDir, meta);
        upload.setTag(UPLOAD_TAG);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        params.topMargin = dp(activity, 8);
        params.bottomMargin = dp(activity, 7);
        int index = Math.min(2, card.getChildCount());
        card.addView(upload, index, params);
    }

    private static Button buildUploadButton(MainActivity activity, File sessionDir, JSONObject meta) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        if (SleepUploadState.wasUploaded(sessionDir, meta)) {
            setUploadedStyle(activity, button);
            return button;
        }

        button.setText("☁ 이 기록 업로드");
        button.setTextColor(Color.WHITE);
        button.setBackground(round(activity, primary(activity), 18, 0, 0));
        button.setOnClickListener(v -> confirmUpload(activity, sessionDir, button));
        return button;
    }

    private static void confirmUpload(MainActivity activity, File sessionDir, Button button) {
        if (!isCompletedOwnedSession(activity, sessionDir)) {
            Toast.makeText(activity, "완료된 수면 기록을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject meta = SessionStore.readMeta(sessionDir);
        if (SleepUploadState.wasUploaded(sessionDir, meta)) {
            setUploadedStyle(activity, button);
            Toast.makeText(activity, "이미 업로드한 기록입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("수면 기록 업로드")
                .setMessage("이 수면 기록 1건을 서버로 업로드할까요?\n\n현재 수면 시간·코골이 후보·분석값·판정 상태만 전송합니다. 전체 녹음과 후보 음원은 전송하지 않습니다.\n\n한 번 업로드하면 이후 판정 내용을 바꾸더라도 다시 보내지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("업로드", (dialog, which) -> startUpload(activity, sessionDir, button))
                .show();
    }

    private static void startUpload(MainActivity activity, File sessionDir, Button button) {
        button.setEnabled(false);
        button.setText("업로드 중…");
        button.setTextColor(muted(activity));
        button.setBackground(round(activity, card2(activity), 18, 1, border(activity)));

        SupabaseSleepUploader.upload(activity, sessionDir, new SupabaseSleepUploader.Callback() {
            @Override public void onSuccess(boolean alreadyUploaded) {
                activity.runOnUiThread(() -> {
                    setUploadedStyle(activity, button);
                    Toast.makeText(activity,
                            alreadyUploaded ? "이미 서버에 업로드된 기록입니다." : "수면 기록 1건을 업로드했습니다.",
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onFailure(String message) {
                activity.runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("☁ 이 기록 업로드");
                    button.setTextColor(Color.WHITE);
                    button.setBackground(round(activity, primary(activity), 18, 0, 0));
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void setUploadedStyle(MainActivity activity, Button button) {
        button.setEnabled(false);
        button.setText("✓ 업로드 완료");
        button.setTextColor(primary(activity));
        button.setBackground(round(activity, card2(activity), 18, 1, border(activity)));
        button.setOnClickListener(null);
    }

    private static void updatePrivacyCopy(View root) {
        TextView homePrivacy = findContainingText(root, "녹음과 분석 기록은 앱 내부에 저장하며 자동 업로드하지 않습니다.");
        if (homePrivacy != null) {
            homePrivacy.setText("녹음과 분석 기록은 기본적으로 앱 내부에만 저장됩니다. 완료된 수면 기록에서 사용자가 직접 업로드를 선택한 경우에만 분석 기록 1건을 전송하며, 녹음 원음과 후보 음원은 보내지 않습니다.");
        }

        TextView settingsPrivacy = findContainingText(root, "수면 기록을 자체 서버로 자동 업로드하지 않습니다.");
        if (settingsPrivacy != null) {
            settingsPrivacy.setText("✓  완료된 수면 기록은 사용자가 직접 선택할 때만 분석 기록 1건을 업로드하며, 녹음은 보내지 않습니다.");
        }
    }

    private static File currentDetailSession(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("detailSession");
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof File ? (File) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCompletedOwnedSession(MainActivity activity, File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        try {
            String root = SessionStore.sessionsRoot(activity).getCanonicalPath() + File.separator;
            if (!dir.getCanonicalPath().startsWith(root)) return false;
        } catch (Exception e) {
            return false;
        }
        JSONObject meta = SessionStore.readMeta(dir);
        return "complete".equals(meta.optString("status"))
                && meta.optLong("endEpochMs", 0) > meta.optLong("startEpochMs", 0);
    }

    private static TextView findExactText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExactText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findContainingText(View view, String value) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().contains(value)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findContainingText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean pink(MainActivity activity) {
        return "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
    }

    private static int primary(MainActivity activity) { return pink(activity) ? 0xFFE94778 : 0xFF159A7A; }
    private static int card2(MainActivity activity) { return pink(activity) ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private static int muted(MainActivity activity) { return pink(activity) ? 0xFF9A7180 : 0xFF718984; }
    private static int border(MainActivity activity) { return pink(activity) ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private static GradientDrawable round(MainActivity activity, int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(activity, strokeDp), strokeColor);
        return drawable;
    }

    private static int dp(MainActivity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
