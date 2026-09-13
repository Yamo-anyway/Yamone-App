package com.yamo.snorelab;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
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

/** v0.25.01: original mockup design, with app-internal visit-history Back. */
public class YamoneDesignPreviewActivity extends Activity {
    private static final String MOCKUP_ROOT = "yamone-v23";
    private static final String MOCKUP_INDEX = MOCKUP_ROOT + "/index.html";
    private static final int BOTTOM_TOUCH_SAFETY_DP = 6;
    private FrameLayout safeRoot;
    private WebView webView;
    private Runnable unregisterSystemBack;
    private boolean backPending;
    private AlertDialog fallbackExitDialog;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBars(false);
        if (Build.VERSION.SDK_INT >= 33) {
            unregisterSystemBack = Api33.register(this, this::dispatchSystemBack);
        }
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
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(), "YamoneNative");
        safeRoot.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        safeRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets topInsets = insets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets bottomInsets = insets.getInsets(
                        WindowInsets.Type.navigationBars() | WindowInsets.Type.mandatorySystemGestures());
                android.graphics.Insets horizontalInsets = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
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
        setContentView(safeRoot);
        safeRoot.requestApplyInsets();
        webView.loadUrl("file:///android_asset/" + MOCKUP_INDEX);
    }

    private boolean mockupAssetsInstalled() {
        try {
            String[] files = getAssets().list(MOCKUP_ROOT);
            if (files != null) for (String name : files) if ("index.html".equals(name)) return true;
        } catch (IOException ignored) { }
        return false;
    }

    private void showMissingAssetNotice() {
        TextView notice = new TextView(this);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(28), dp(28), dp(28), dp(28));
        notice.setTextColor(Color.rgb(21, 54, 51));
        notice.setTextSize(16f);
        notice.setBackgroundColor(Color.rgb(247, 255, 251));
        notice.setText("야모네 디자인 소스가 아직 APK에 포함되지 않았습니다.\n\n"
                + "design-source/yamone-v23.zip 을 추가하면\n"
                + "기본 디자인에 v0.25.01 패치를 적용해 빌드합니다.");
        setContentView(notice);
    }

    private void applySystemBars(boolean pink) {
        int color = Color.parseColor(pink ? "#FFFAFB" : "#FBFDFC");
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class NativeBridge {
        @JavascriptInterface public void setTheme(String theme) {
            runOnUiThread(() -> applySystemBars("pink".equals(theme)));
        }
        @JavascriptInterface public void finishApp() {
            runOnUiThread(() -> exitConfirmed());
        }
    }

    // Android <= 12 uses this entry point; Android 13+ uses Api33 below.
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        dispatchSystemBack();
    }

    private void dispatchSystemBack() {
        if (isFinishing() || isDestroyed() || backPending) return;
        if (webView == null) { showFallbackExitConfirm(); return; }
        backPending = true;
        webView.evaluateJavascript(
                "Boolean(window.yamoneAndroidBack && window.yamoneAndroidBack())",
                value -> {
                    backPending = false;
                    if (isFinishing() || isDestroyed()) return;
                    // Loading/JS failure must never cause an unconfirmed app exit.
                    if (!"true".equals(value)) showFallbackExitConfirm();
                });
    }

    private void showFallbackExitConfirm() {
        if (isFinishing() || isDestroyed()) return;
        if (fallbackExitDialog != null && fallbackExitDialog.isShowing()) return;
        fallbackExitDialog = new AlertDialog.Builder(this)
                .setTitle("야모네를 나갈까요?")
                .setMessage("나가기를 누르면 앱을 종료합니다.")
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("나가기", (dialog, which) -> exitConfirmed())
                .create();
        fallbackExitDialog.show();
    }

    private void exitConfirmed() {
        if (isFinishing() || isDestroyed()) return;
        // Do not launch HOME or another app. Android reveals the prior task/screen.
        if (isTaskRoot()) finishAndRemoveTask();
        else finish();
    }

    @TargetApi(33)
    private static final class Api33 {
        static Runnable register(Activity activity, Runnable onBack) {
            android.window.OnBackInvokedCallback callback = onBack::run;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
        }
    }

    @Override protected void onDestroy() {
        if (unregisterSystemBack != null) { unregisterSystemBack.run(); unregisterSystemBack = null; }
        if (fallbackExitDialog != null) { fallbackExitDialog.dismiss(); fallbackExitDialog = null; }
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
