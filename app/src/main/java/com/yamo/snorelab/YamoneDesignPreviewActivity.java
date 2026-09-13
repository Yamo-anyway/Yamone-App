package com.yamo.snorelab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.IOException;

/**
 * Yamone v0.24.01 design-review host.
 *
 * This Activity intentionally runs the approved mockup plus the current
 * design-check patch only. GPS, alarm scheduling, sleep recording and the
 * other production services are not connected at this stage.
 */
public class YamoneDesignPreviewActivity extends Activity {
    private static final String MOCKUP_ROOT = "yamone-v23";
    private static final String MOCKUP_INDEX = MOCKUP_ROOT + "/index.html";
    private static final int BOTTOM_TOUCH_SAFETY_DP = 6;

    private FrameLayout safeRoot;
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBars(false);

        if (!mockupAssetsInstalled()) {
            showMissingAssetNotice();
            return;
        }

        safeRoot = new FrameLayout(this);
        safeRoot.setBackgroundColor(Color.rgb(251, 253, 252));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(251, 253, 252));
        webView.setPadding(0, 0, 0, 0);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setTextZoom(100);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(), "YamoneNative");

        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        safeRoot.addView(webView, webParams);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            safeRoot.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                int bottom;
                int left;
                int right;

                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets topInsets = insets.getInsets(
                            WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()
                    );
                    android.graphics.Insets bottomInsets = insets.getInsets(
                            WindowInsets.Type.navigationBars() | WindowInsets.Type.mandatorySystemGestures()
                    );
                    android.graphics.Insets horizontalInsets = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                    );
                    top = topInsets.top;
                    bottom = bottomInsets.bottom;
                    left = horizontalInsets.left;
                    right = horizontalInsets.right;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft();
                    right = insets.getSystemWindowInsetRight();
                }

                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) webView.getLayoutParams();
                lp.leftMargin = left;
                lp.topMargin = top;
                lp.rightMargin = right;
                lp.bottomMargin = bottom + dp(BOTTOM_TOUCH_SAFETY_DP);
                webView.setLayoutParams(lp);
                return insets;
            });
            safeRoot.requestApplyInsets();
        }

        setContentView(safeRoot);
        webView.loadUrl("file:///android_asset/" + MOCKUP_INDEX);
    }

    private boolean mockupAssetsInstalled() {
        try {
            String[] files = getAssets().list(MOCKUP_ROOT);
            if (files == null) return false;
            for (String name : files) {
                if ("index.html".equals(name)) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private void showMissingAssetNotice() {
        TextView notice = new TextView(this);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(28), dp(28), dp(28), dp(28));
        notice.setTextColor(Color.rgb(21, 54, 51));
        notice.setTextSize(16f);
        notice.setBackgroundColor(Color.rgb(247, 255, 251));
        notice.setText(
                "야모네 디자인 소스가 아직 APK에 포함되지 않았습니다.\n\n" +
                "design-source/yamone-v23.zip 을 추가하면\n" +
                "기본 디자인에 v0.24.01 점검 패치를 적용해 빌드합니다.\n\n" +
                "실제 알람 · 수면 · 활동 기능 코드는 변경되지 않았습니다."
        );
        setContentView(notice);
    }

    private void applySystemBars(boolean pink) {
        int color = Color.parseColor(pink ? "#FFFAFB" : "#FBFDFC");
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class NativeBridge {
        @JavascriptInterface public void setTheme(String theme) {
            runOnUiThread(() -> applySystemBars("pink".equals(theme)));
        }

        @JavascriptInterface public void finishApp() {
            runOnUiThread(() -> finish());
        }
    }

    @Override public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "String(!!(window.yamoneAndroidBack && window.yamoneAndroidBack()))",
                value -> {
                    if (!"\"true\"".equals(value) && !"true".equals(value)) {
                        YamoneDesignPreviewActivity.super.onBackPressed();
                    }
                }
        );
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface("YamoneNative");
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        safeRoot = null;
        super.onDestroy();
    }
}
