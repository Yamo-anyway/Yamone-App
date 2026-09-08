package com.yamo.snorelab;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayDeque;

/** Turns one-second detector decisions into reviewable candidate events and optional WAV clips. */
public final class CandidateEventAccumulator {
    private static final int PRE_ROLL_WINDOWS = 3;
    private static final int END_AFTER_NON_CANDIDATE_WINDOWS = 2;
    private static final int MAX_EVENT_WINDOWS = 60;

    private final File sessionDir;
    private final int sampleRate;
    private final boolean saveClips;
    private final ArrayDeque<short[]> preRoll = new ArrayDeque<>();

    private boolean active = false;
    private ByteArrayOutputStream clipPcm;
    private long eventStartOffsetMs;
    private long lastCandidateEndOffsetMs;
    private int activeWindows;
    private int trailingQuiet;
    private double scoreSum;
    private int scoreCount;
    private double scoreMax;
    private double dbfsSum;
    private double dbfsMax = -120;
    private double lowRatioSum;
    private double periodicitySum;
    private double periodicityMax;
    private double zeroCrossRateSum;
    private double thresholdSum;
    private int nextIndex = 1;

    public CandidateEventAccumulator(File sessionDir, int sampleRate, boolean saveClips, int existingEvents) {
        this.sessionDir = sessionDir;
        this.sampleRate = sampleRate;
        this.saveClips = saveClips;
        this.nextIndex = existingEvents + 1;
    }

    public void onWindow(short[] window, long windowStartOffsetMs, SnoreDetector.Result result) throws Exception {
        if (!active && result.candidate) start(windowStartOffsetMs);

        if (active) {
            appendPcm(window);
            activeWindows++;
            if (result.candidate) {
                trailingQuiet = 0;
                lastCandidateEndOffsetMs = windowStartOffsetMs + 1000;
                scoreSum += result.score;
                scoreCount++;
                scoreMax = Math.max(scoreMax, result.score);
                dbfsSum += result.dbfs;
                dbfsMax = Math.max(dbfsMax, result.dbfs);
                lowRatioSum += result.lowBandRatio;
                periodicitySum += result.periodicity;
                periodicityMax = Math.max(periodicityMax, result.periodicity);
                zeroCrossRateSum += result.zeroCrossRate;
                thresholdSum += result.threshold;
            } else {
                trailingQuiet++;
            }

            if (trailingQuiet >= END_AFTER_NON_CANDIDATE_WINDOWS || activeWindows >= MAX_EVENT_WINDOWS) {
                finish();
            }
        }

        short[] copy = window.clone();
        preRoll.addLast(copy);
        while (preRoll.size() > PRE_ROLL_WINDOWS) preRoll.removeFirst();
    }

    public void flush() throws Exception {
        if (active) finish();
    }

    private void start(long windowStartOffsetMs) {
        active = true;
        eventStartOffsetMs = windowStartOffsetMs;
        lastCandidateEndOffsetMs = windowStartOffsetMs + 1000;
        activeWindows = 0;
        trailingQuiet = 0;
        scoreSum = 0;
        scoreCount = 0;
        scoreMax = 0;
        dbfsSum = 0;
        dbfsMax = -120;
        lowRatioSum = 0;
        periodicitySum = 0;
        periodicityMax = 0;
        zeroCrossRateSum = 0;
        thresholdSum = 0;
        clipPcm = saveClips ? new ByteArrayOutputStream((PRE_ROLL_WINDOWS + 12) * sampleRate * 2) : null;
        if (saveClips) {
            for (short[] pre : preRoll) appendPcm(pre);
        }
    }

    private void finish() throws Exception {
        long end = Math.max(lastCandidateEndOffsetMs, eventStartOffsetMs + 1000);
        long duration = Math.max(1000, end - eventStartOffsetMs);
        int idx = nextIndex++;
        String clipName = null;
        if (saveClips && clipPcm != null) {
            clipName = String.format(java.util.Locale.US, "event_%03d.wav", idx);
            File clip = new File(new File(sessionDir, "clips"), clipName);
            WavWriter.writePcm16Mono(clip, clipPcm.toByteArray(), sampleRate);
        }

        JSONObject e = new JSONObject();
        e.put("index", idx - 1);
        e.put("displayNumber", idx);
        e.put("startOffsetMs", eventStartOffsetMs);
        e.put("endOffsetMs", end);
        e.put("durationMs", duration);
        e.put("scoreAvg", scoreCount == 0 ? 0 : scoreSum / scoreCount);
        e.put("scoreMax", scoreMax);
        e.put("dbfsAvg", scoreCount == 0 ? 0 : dbfsSum / scoreCount);
        e.put("dbfsMax", dbfsMax);
        e.put("lowBandRatioAvg", scoreCount == 0 ? 0 : lowRatioSum / scoreCount);
        e.put("periodicityAvg", scoreCount == 0 ? 0 : periodicitySum / scoreCount);
        e.put("periodicityMax", periodicityMax);
        e.put("zeroCrossRateAvg", scoreCount == 0 ? 0 : zeroCrossRateSum / scoreCount);
        e.put("thresholdAvg", scoreCount == 0 ? 0 : thresholdSum / scoreCount);
        e.put("candidateWindowCount", scoreCount);
        e.put("reviewLabel", "UNREVIEWED");
        e.put("clipFile", clipName == null ? JSONObject.NULL : "clips/" + clipName);
        e.put("preRollMs", saveClips ? PRE_ROLL_WINDOWS * 1000 : 0);
        SessionStore.appendEvent(sessionDir, e);

        active = false;
        clipPcm = null;
    }

    private void appendPcm(short[] pcm) {
        if (!saveClips || clipPcm == null) return;
        for (short s : pcm) {
            clipPcm.write(s & 0xff);
            clipPcm.write((s >>> 8) & 0xff);
        }
    }
}
