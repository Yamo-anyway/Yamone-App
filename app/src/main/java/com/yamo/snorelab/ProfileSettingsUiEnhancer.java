package com.yamo.snorelab;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.WeakHashMap;

/** Adds app-wide nickname settings without rewriting MainActivity's existing settings layout. */
public final class ProfileSettingsUiEnhancer {
    private static final String PROFILE_TAG = "yamone_profile_settings_v1";
    private static final WeakHashMap<MainActivity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS = new WeakHashMap<>();

    private ProfileSettingsUiEnhancer() {}

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
        TextView settingsTitle = findExactText(root, "수면 설정");
        TextView themeTitle = findExactText(root, "테마");
        if (settingsTitle == null || themeTitle == null) return;

        settingsTitle.setText("설정");
        TextView subtitle = findExactText(root, "테마와 마이크 측정을 편하게 조절해요.");
        if (subtitle != null) subtitle.setText("앱 기본 설정과 수면 측정을 편하게 조절해요.");

        View themeCardView = themeTitle.getParent() instanceof View ? (View) themeTitle.getParent() : null;
        if (themeCardView == null || !(themeCardView.getParent() instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) themeCardView.getParent();
        if (page.findViewWithTag(PROFILE_TAG) != null) return;

        LinearLayout card = new LinearLayout(activity);
        card.setTag(PROFILE_TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
        card.setBackground(round(activity, cardColor(activity), 24, 1, border(activity)));

        card.addView(text(activity, "프로필", 15, textColor(activity), true));
        TextView help = text(activity,
                "위치 공유 방을 만들거나 참여할 때 기본으로 사용할 닉네임입니다. 방 안에서는 그 방에서만 별도로 바꿀 수 있어요.",
                11, muted(activity), false);
        help.setPadding(0, dp(activity, 5), 0, dp(activity, 10));
        card.addView(help);

        EditText nickname = new EditText(activity);
        nickname.setSingleLine(true);
        nickname.setText(LocationProfileStore.getNickname(activity));
        nickname.setSelection(nickname.getText().length());
        nickname.setHint("닉네임");
        nickname.setHintTextColor(muted(activity));
        nickname.setTextColor(textColor(activity));
        nickname.setTextSize(14);
        nickname.setInputType(InputType.TYPE_CLASS_TEXT);
        nickname.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        nickname.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        nickname.setBackground(round(activity, card2(activity), 16, 1, border(activity)));
        card.addView(nickname, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));

        TextView note = text(activity, "1~24자 · 기본값은 ‘사용자’", 10, muted(activity), false);
        note.setPadding(0, dp(activity, 5), 0, dp(activity, 8));
        card.addView(note);

        Button save = new Button(activity);
        save.setAllCaps(false);
        save.setText("기본 닉네임 저장");
        save.setTextSize(13);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setTextColor(Color.WHITE);
        save.setBackground(round(activity, primary(activity), 18, 0, 0));
        save.setOnClickListener(v -> {
            String value = nickname.getText().toString().trim();
            if (value.isEmpty()) {
                value = "사용자";
                nickname.setText(value);
                nickname.setSelection(value.length());
            }
            if (value.length() > 24) {
                Toast.makeText(activity, "닉네임은 24자 이내로 입력해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            LocationProfileStore.setNickname(activity, value);
            Toast.makeText(activity, "기본 닉네임을 저장했습니다.", Toast.LENGTH_SHORT).show();
        });
        card.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, 12);
        int index = page.indexOfChild(themeCardView);
        page.addView(card, Math.max(0, index), params);
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

    private static TextView text(MainActivity activity, String value, int sp, int color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private static boolean pink(MainActivity activity) {
        return "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
    }

    private static int primary(MainActivity activity) { return pink(activity) ? 0xFFE94778 : 0xFF159A7A; }
    private static int textColor(MainActivity activity) { return pink(activity) ? 0xFF4B2633 : 0xFF153633; }
    private static int muted(MainActivity activity) { return pink(activity) ? 0xFF9A7180 : 0xFF718984; }
    private static int cardColor(MainActivity activity) { return 0xFFFFFFFF; }
    private static int card2(MainActivity activity) { return pink(activity) ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private static int border(MainActivity activity) { return pink(activity) ? 0xFFFFE3EC : 0xFFE0F3EC; }

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
