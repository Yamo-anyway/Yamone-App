package com.yamo.snorelab;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/** Adds share-time controls while keeping the Yamone mint/pink theme. */
public class LocationSharingActivityV2 extends LocationSharingActivity {
    private static final String EXTEND_TAG = "yamone_location_time_extend";
    private static final String INTERVAL_TAG = "yamone_location_interval_minute_only_v1";
    private final Handler enhancer = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleEnhance();
    }

    @Override protected void onResume() {
        super.onResume();
        scheduleEnhance();
    }

    private void scheduleEnhance() {
        enhancer.removeCallbacksAndMessages(null);
        enhancer.postDelayed(this::enhance, 150L);
        enhancer.postDelayed(this::enhance, 700L);
        enhancer.postDelayed(this::enhance, 1_500L);
    }

    private void enhance() {
        View root = findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;

        enforceMinuteOnlyInterval(group);
        TextView oldBattery = findContaining(group, "10초는 위치가 빠르게");
        if (oldBattery != null) {
            oldBattery.setText("1분 갱신을 기본으로 사용하고, 배터리 절약이 더 필요하면 3분을 선택할 수 있어요.");
        }

        TextView leave = findExact(group, "위치 공유 종료 · 방 나가기");
        if (leave == null || !(leave.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) leave.getParent();
        if (parent.findViewWithTag(EXTEND_TAG) != null) return;

        TextView extend = new TextView(this);
        extend.setTag(EXTEND_TAG);
        extend.setText("⏱  공유 시간 연장 / 변경");
        extend.setTextColor(primary2());
        extend.setTextSize(14);
        extend.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        extend.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card2());
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), border());
        extend.setBackground(bg);
        extend.setOnClickListener(v -> startActivity(new Intent(this, LocationSharingTimeActivity.class)));

        int index = parent.indexOfChild(leave);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(10);
        if (index >= 0) parent.addView(extend, index, params);
        else parent.addView(extend, params);
    }

    private void enforceMinuteOnlyInterval(View root) {
        Spinner spinner = findFirstSpinner(root);
        if (spinner == null || INTERVAL_TAG.equals(spinner.getTag())) return;
        spinner.setTag(INTERVAL_TAG);

        // Keep four backing positions because LocationSharingActivity maps positions 2 and 3
        // to 60 and 180 seconds. Positions 0 and 1 are hidden and disabled.
        String[] values = {"", "", "1분", "3분"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public boolean isEnabled(int position) {
                return position >= 2;
            }

            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = choiceView(position >= 2 ? values[position] : "1분", false);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                if (position < 2) {
                    TextView hidden = new TextView(LocationSharingActivityV2.this);
                    hidden.setVisibility(View.GONE);
                    hidden.setLayoutParams(new android.widget.AbsListView.LayoutParams(1, 1));
                    return hidden;
                }
                return choiceView(values[position], true);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(2, false);
    }

    private TextView choiceView(String value, boolean dropdown) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(14);
        view.setTextColor(primary2());
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card2());
        bg.setCornerRadius(dp(dropdown ? 12 : 14));
        if (dropdown) bg.setStroke(dp(1), border());
        view.setBackground(bg);
        view.setMinHeight(dp(48));
        return view;
    }

    private Spinner findFirstSpinner(View view) {
        if (view instanceof Spinner) return (Spinner) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Spinner found = findFirstSpinner(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findExact(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExact(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findContaining(View view, String text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(text)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findContaining(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
    }

    private int card2() { return pink() ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private int primary2() { return pink() ? 0xFFE94778 : 0xFF159A7A; }
    private int border() { return pink() ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        enhancer.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
