package com.yamo.snorelab;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MiniGameActivity extends Activity {
    private static final String KEY_THEME = "yamone_theme";

    private int BG;
    private int CARD;
    private int CARD2;
    private int TEXT;
    private int MUTED;
    private int PRIMARY2;
    private FrameLayout content;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE);
        applyTheme();
        buildRoot();
        showPlaceholder();
    }

    private void applyTheme() {
        boolean pink = "pink".equals(prefs.getString(KEY_THEME, "mint"));
        BG = pink ? 0xFFFFF7FA : 0xFFF7FFFB;
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setBackgroundColor(CARD);
        nav.addView(navItem("⏰\n알람", false, v -> go(AlarmActivity.class)), new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(navItem("☾\n수면", false, v -> go(MainActivity.class)), new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(navItem("🏃\n활동", false, v -> go(ExerciseActivity.class)), new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(navItem("🎮\n미니게임", true, v -> showPlaceholder()), new LinearLayout.LayoutParams(0, dp(60), 1f));
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Build.VERSION.SDK_INT >= 21) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                    bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(0, top + dp(4), 0, bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        setContentView(root);
    }

    private void showPlaceholder() {
        content.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(BG);

        TextView icon = text("🎮", 54, PRIMARY2, false);
        icon.setGravity(Gravity.CENTER);
        page.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        TextView title = text("미니게임", 26, TEXT, true);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        TextView desc = text("나중에 야모네 미니게임을 연결할 자리예요.", 13, MUTED, false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(10), 0, 0);
        page.addView(desc);

        content.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }

    private TextView navItem(String label, boolean selected, View.OnClickListener click) {
        TextView v = text(label, 12, selected ? PRIMARY2 : MUTED, true);
        v.setGravity(Gravity.CENTER);
        if (selected) v.setBackground(round(CARD2, 19));
        v.setOnClickListener(click);
        return v;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
