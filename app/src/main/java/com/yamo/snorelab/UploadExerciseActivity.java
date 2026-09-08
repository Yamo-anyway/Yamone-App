package com.yamo.snorelab;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;

/**
 * Adds opt-in upload controls without changing the existing activity design.
 * Upload is only offered for a completed, locally saved record.
 */
public class UploadExerciseActivity extends EnhancedExerciseActivity {
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY = 0xFF6D72FF;
    private static final int SUCCESS = 0xFF61D6A8;
    private static final String UPLOAD_TAG = "yamone_activity_upload_button_v1";

    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::enhanceUploadUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        decor.post(this::enhanceUploadUi);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().post(this::enhanceUploadUi);
    }

    @Override
    protected void onDestroy() {
        View decor = getWindow().getDecorView();
        if (decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        }
        super.onDestroy();
    }

    private void enhanceUploadUi() {
        View decor = getWindow().getDecorView();
        updatePrivacyCopy(decor);

        TextView deleteButton = findExactText(decor, "기록 삭제");
        if (deleteButton == null || !(deleteButton.getParent() instanceof ViewGroup)) return;
        ViewGroup page = (ViewGroup) deleteButton.getParent();
        if (page.findViewWithTag(UPLOAD_TAG) != null) return;

        File dir = WalkingStore.getLastReadMetaDir();
        if (!isOwnedCompletedSession(dir)) return;

        Button upload = buildUploadButton(dir);
        upload.setTag(UPLOAD_TAG);
        int index = page.indexOfChild(deleteButton);
        if (index < 0) return;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(2);
        params.bottomMargin = dp(8);
        page.addView(upload, index, params);
    }

    private Button buildUploadButton(File dir) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        if (WalkingStore.wasUploaded(dir)) {
            setUploadedStyle(button);
            return button;
        }

        button.setText("☁ 이 기록 업로드");
        button.setTextColor(Color.WHITE);
        button.setBackground(round(PRIMARY, 16, 0, 0));
        button.setOnClickListener(v -> confirmUpload(dir, button));
        return button;
    }

    private void confirmUpload(File dir, Button button) {
        if (!isOwnedCompletedSession(dir)) {
            Toast.makeText(this, "완료된 활동 기록을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (WalkingStore.wasUploaded(dir)) {
            setUploadedStyle(button);
            Toast.makeText(this, "이미 업로드한 기록입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("활동 기록 업로드")
                .setMessage("이 활동 기록 1건을 서버로 업로드할까요?\n\n자동 업로드는 하지 않으며, 이 기록은 한 번 업로드하면 다시 보내지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("업로드", (dialog, which) -> startUpload(dir, button))
                .show();
    }

    private void startUpload(File dir, Button button) {
        button.setEnabled(false);
        button.setText("업로드 중…");
        button.setTextColor(MUTED);
        button.setBackground(round(CARD2, 16, 1, 0xFF35445F));

        SupabaseActivityUploader.upload(this, dir, new SupabaseActivityUploader.Callback() {
            @Override
            public void onSuccess(boolean alreadyUploaded) {
                runOnUiThread(() -> {
                    setUploadedStyle(button);
                    Toast.makeText(UploadExerciseActivity.this,
                            alreadyUploaded ? "이미 서버에 업로드된 기록입니다." : "활동 기록 1건을 업로드했습니다.",
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("☁ 이 기록 업로드");
                    button.setTextColor(Color.WHITE);
                    button.setBackground(round(PRIMARY, 16, 0, 0));
                    Toast.makeText(UploadExerciseActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setUploadedStyle(Button button) {
        button.setEnabled(false);
        button.setText("✓ 업로드 완료");
        button.setTextColor(SUCCESS);
        button.setBackground(round(CARD2, 16, 1, 0xFF35445F));
        button.setOnClickListener(null);
    }

    private void updatePrivacyCopy(View root) {
        TextView privacy = findContainingText(root, "GPS 경로와 활동 기록은 휴대폰 내부에만 저장됩니다.");
        if (privacy == null) return;
        privacy.setText("GPS 경로와 활동 기록은 기본적으로 휴대폰 내부에만 저장됩니다. 사용자가 완료된 기록에서 ‘이 기록 업로드’를 직접 선택한 경우에만 해당 기록 1건을 서버로 전송합니다. 자동 업로드는 하지 않습니다. 지도 배경을 표시할 때만 OpenFreeMap 지도 타일을 인터넷으로 불러옵니다.");
    }

    private boolean isOwnedCompletedSession(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        try {
            String rootPath = WalkingStore.root(this).getCanonicalPath() + File.separator;
            String dirPath = dir.getCanonicalPath();
            if (!dirPath.startsWith(rootPath)) return false;
        } catch (Exception e) {
            return false;
        }
        JSONObject meta = WalkingStore.readMeta(dir);
        return "complete".equals(meta.optString("status"));
    }

    private TextView findExactText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExactText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findContainingText(View view, String value) {
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

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
