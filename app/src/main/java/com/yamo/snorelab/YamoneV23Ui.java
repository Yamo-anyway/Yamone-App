package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Common Android View builders matching Yamone Renewal v0.00.23. */
public final class YamoneV23Ui {
    private YamoneV23Ui() {}

    public static LinearLayout verticalCard(Context context, YamoneV23Theme.Palette palette) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(YamoneV23Theme.card(context, palette));
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(YamoneV23Theme.dp(context, 2f));
        return card;
    }

    public static LinearLayout horizontalCard(Context context, YamoneV23Theme.Palette palette) {
        LinearLayout card = verticalCard(context, palette);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        return card;
    }

    public static TextView label(Context context, String text, YamoneV23Theme.Palette palette,
                                 float textSp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(palette.text);
        view.setTextSize(textSp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static TextView muted(Context context, String text, YamoneV23Theme.Palette palette, float textSp) {
        TextView view = label(context, text, palette, textSp, false);
        view.setTextColor(palette.muted);
        return view;
    }

    public static ImageView asset(Context context, String sourceName, int sizeDp) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int resource = YamoneV23Assets.id(context, sourceName);
        if (resource != 0) image.setImageResource(resource);
        int size = YamoneV23Theme.dp(context, sizeDp);
        image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return image;
    }

    public static FrameLayout navButton(Context context, String sourceName, String title,
                                        boolean selected, YamoneV23Theme.Palette palette) {
        FrameLayout frame = new FrameLayout(context);
        if (selected) frame.setBackground(YamoneV23Theme.navSelection(context, palette));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, YamoneV23Theme.dp(context, 3), 0, YamoneV23Theme.dp(context, 3));
        body.addView(asset(context, sourceName, 34));

        TextView text = label(context, title, palette, 10f, true);
        text.setTextColor(selected ? palette.primary : palette.navInactive);
        text.setGravity(Gravity.CENTER);
        body.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        frame.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }
}
