package com.yamo.snorelab;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** Compatibility entry point plus visual polish matching the approved Yamone location-sharing storyboard. */
public class LocationSharingActivityV2 extends LocationSharingActivity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String ART_TAG = "yamone_location_scene_art_v4";

    private final Runnable polishLoop = new Runnable() {
        @Override public void run() {
            polish();
            ui.postDelayed(this, 5_000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        schedulePolish();
    }

    @Override protected void onResume() {
        super.onResume();
        schedulePolish();
    }

    @Override protected void onPause() {
        ui.removeCallbacks(polishLoop);
        super.onPause();
    }

    private void schedulePolish() {
        ui.removeCallbacks(polishLoop);
        ui.postDelayed(this::polish, 80L);
        ui.postDelayed(this::polish, 280L);
        ui.postDelayed(this::polish, 700L);
        ui.postDelayed(this::polish, 1_400L);
        ui.postDelayed(polishLoop, 5_000L);
    }

    private void polish() {
        View root = findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        fixLandingBack(root);
        replaceLandingIllustration(root);
        replaceStopAction(root);
        polishBottomSpacing(root);
    }

    private void fixLandingBack(View root) {
        TextView title = findExact(root, "위치 공유");
        TextView back = findExact(root, "←");
        if (title != null && back != null) back.setOnClickListener(v -> finish());
    }

    private void replaceLandingIllustration(View root) {
        TextView placeholder = findContaining(root, "🏔️");
        if (placeholder == null || !(placeholder.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        if (parent.findViewWithTag(ART_TAG) != null) return;
        int index = parent.indexOfChild(placeholder);
        ViewGroup.LayoutParams params = placeholder.getLayoutParams();
        parent.removeView(placeholder);
        YamonePastelArtView art = new YamonePastelArtView(this, YamonePastelArtView.MODE_LOCATION_SCENE);
        art.setTag(ART_TAG);
        parent.addView(art, Math.max(0, index), params);
    }

    private void replaceStopAction(View root) {
        TextView stop = findExact(root, "공유 중단");
        if (stop == null) stop = findExact(root, "위치 공유 중단하기");
        if (stop != null) stop.setOnClickListener(v -> showYamoneStopDialog());
    }


    private void polishBottomSpacing(View root) {
        TextView create = findExact(root, "방 만들기");
        TextView join = findExact(root, "방 참여하기");
        TextView primary = create != null ? create : join;
        if (primary != null && primary.getParent() instanceof View) {
            View parent = (View) primary.getParent();
            parent.setPadding(parent.getPaddingLeft(), Math.max(parent.getPaddingTop(), dp(10)),
                    parent.getPaddingRight(), Math.max(parent.getPaddingBottom(), dp(18)));
        }
        addBottomMargin(findExact(root, "중복 확인"), 8);
        addBottomMargin(findExact(root, "공유 시간 연장하기"), 12);
        addBottomMargin(findExact(root, "위치 공유 중단하기"), 22);
    }

    private void addBottomMargin(View view, int marginDp) {
        if (view == null) return;
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) raw;
            p.bottomMargin = Math.max(p.bottomMargin, dp(marginDp));
            view.setLayoutParams(p);
        }
    }





    private void showYamoneStopDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(18), dp(22), dp(20));
        card.setBackground(round(Color.WHITE, 24, 0, 0));

        YamonePastelArtView bunny = new YamonePastelArtView(this, YamonePastelArtView.MODE_BUNNY);
        card.addView(bunny, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView title = text("위치 공유를 중단할까요?", 18, textColor(), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(6), 0, dp(6));
        card.addView(title);

        TextView desc = text("공유를 중단하면 내 위치가 더 이상 다른 참여자에게 표시되지 않아요.", 12, muted(), false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(dp(8), 0, dp(8), dp(14));
        card.addView(desc);

        TextView stop = text("공유 중단하기", 14, 0xFFE75B6D, true);
        stop.setGravity(Gravity.CENTER);
        stop.setBackground(round(0xFFFFE4EA, 17, 0, 0));
        stop.setOnClickListener(v -> {
            stop.setEnabled(false);
            stop.setText("종료하는 중…");
            LocationSharingApi.leave(this, new LocationSharingApi.JsonCallback() {
                @Override public void onSuccess(JSONObject data) {
                    runOnUiThread(() -> {
                        LocationSharingStateStore.clear(LocationSharingActivityV2.this);
                        LocationSharingService.stop(LocationSharingActivityV2.this);
                        dialog.dismiss();
                        Toast.makeText(LocationSharingActivityV2.this, "위치 공유를 종료했습니다.", Toast.LENGTH_SHORT).show();
                        recreate();
                    });
                }
                @Override public void onFailure(String message) {
                    runOnUiThread(() -> {
                        stop.setEnabled(true);
                        stop.setText("공유 중단하기");
                        Toast.makeText(LocationSharingActivityV2.this, message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        stp.bottomMargin = dp(2);
        card.addView(stop, stp);

        TextView cancel = text("취소", 14, primary2(), true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(round(card2(), 17, 1, border()));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        cp.topMargin = dp(9);
        cp.bottomMargin = dp(2);
        card.addView(cancel, cp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(card);
        styleDialog(dialog, 0.86f);
        dialog.show();
        styleDialog(dialog, 0.86f);
    }

    private void styleDialog(Dialog dialog, float widthRatio) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * widthRatio);
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.dimAmount = 0.48f;
        window.setAttributes(lp);
    }

    private TextView findExact(View view, String wanted) {
        if (view instanceof TextView && wanted.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExact(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findContaining(View view, String wanted) {
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && t.toString().contains(wanted)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findContaining(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
    }

    private int textColor() { return pink() ? 0xFF4B2633 : 0xFF153633; }
    private int muted() { return pink() ? 0xFF9A7180 : 0xFF718984; }
    private int primary() { return pink() ? 0xFFFF769F : 0xFF56D1B3; }
    private int primary2() { return pink() ? 0xFFE94778 : 0xFF159A7A; }
    private int card2() { return pink() ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private int border() { return pink() ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
