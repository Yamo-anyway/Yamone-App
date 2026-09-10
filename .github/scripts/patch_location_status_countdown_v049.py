from pathlib import Path

path = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
text = path.read_text()

repls = []
repls.append((
'''    private TextView activeStatusHint;\n    private String currentSelfStatus = "normal";\n\n    private final Runnable activePoller = new Runnable() {\n        @Override public void run() {\n            if (!activeScreen) return;\n            refreshActiveSnapshot();\n            handler.postDelayed(this, ACTIVE_POLL_MS);\n        }\n    };\n''',
'''    private TextView activeStatusHint;\n    private TextView nextStatusCheck;\n    private long nextStatusCheckAtMs;\n    private String currentSelfStatus = "normal";\n\n    private final Runnable activePoller = new Runnable() {\n        @Override public void run() {\n            if (!activeScreen) return;\n            refreshActiveSnapshot();\n            nextStatusCheckAtMs = System.currentTimeMillis() + ACTIVE_POLL_MS;\n            updateStatusCountdownText();\n            handler.postDelayed(this, ACTIVE_POLL_MS);\n        }\n    };\n\n    private final Runnable statusCountdown = new Runnable() {\n        @Override public void run() {\n            if (!activeScreen) return;\n            updateStatusCountdownText();\n            handler.postDelayed(this, 1_000L);\n        }\n    };\n'''))

repls.append((
'''    @Override protected void onPause() {\n        handler.removeCallbacks(activePoller);\n        super.onPause();\n    }\n''',
'''    @Override protected void onPause() {\n        handler.removeCallbacks(activePoller);\n        handler.removeCallbacks(statusCountdown);\n        super.onPause();\n    }\n'''))

repls.append((
'''        activeNetworkHint = null;\n        activeStatusHint = null;\n\n        LinearLayout root = rootShell();\n''',
'''        activeNetworkHint = null;\n        activeStatusHint = null;\n        nextStatusCheck = null;\n\n        LinearLayout root = rootShell();\n'''))

repls.append((
'''        LinearLayout myStatus = card();\n        myStatus.setPadding(dp(12), dp(10), dp(12), dp(10));\n        myStatus.addView(text("내 상태", 13, TEXT, true));\n\n        LinearLayout statusRow = new LinearLayout(this);\n''',
'''        LinearLayout myStatus = card();\n        myStatus.setPadding(dp(12), dp(10), dp(12), dp(10));\n\n        LinearLayout statusHeader = new LinearLayout(this);\n        statusHeader.setOrientation(LinearLayout.HORIZONTAL);\n        statusHeader.setGravity(Gravity.CENTER_VERTICAL);\n        TextView statusTitle = text("내 상태", 13, TEXT, true);\n        statusTitle.setIncludeFontPadding(false);\n        statusHeader.addView(statusTitle, new LinearLayout.LayoutParams(\n                0, dp(24), 1f));\n        nextStatusCheck = text("다음 상태 확인 60초", 10, MUTED, true);\n        nextStatusCheck.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);\n        nextStatusCheck.setSingleLine(true);\n        nextStatusCheck.setIncludeFontPadding(false);\n        statusHeader.addView(nextStatusCheck, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)));\n        myStatus.addView(statusHeader);\n\n        LinearLayout statusRow = new LinearLayout(this);\n'''))

repls.append((
'''    private void scheduleActivePolling() {\n        handler.removeCallbacks(activePoller);\n        if (activeScreen) handler.postDelayed(activePoller, ACTIVE_POLL_MS);\n    }\n''',
'''    private void scheduleActivePolling() {\n        handler.removeCallbacks(activePoller);\n        handler.removeCallbacks(statusCountdown);\n        if (!activeScreen) return;\n        nextStatusCheckAtMs = System.currentTimeMillis() + ACTIVE_POLL_MS;\n        updateStatusCountdownText();\n        handler.postDelayed(activePoller, ACTIVE_POLL_MS);\n        handler.postDelayed(statusCountdown, 1_000L);\n    }\n\n    private void updateStatusCountdownText() {\n        if (nextStatusCheck == null) return;\n        long remainingMs = Math.max(0L, nextStatusCheckAtMs - System.currentTimeMillis());\n        long seconds = (remainingMs + 999L) / 1_000L;\n        nextStatusCheck.setText("다음 상태 확인 " + seconds + "초");\n    }\n'''))

for old, new in repls:
    if old not in text:
        raise SystemExit('missing patch anchor:\n' + old[:180])
    text = text.replace(old, new, 1)

path.write_text(text)
print('patched location status countdown')
