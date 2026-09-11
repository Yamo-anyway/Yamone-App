package com.yamo.snorelab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class YamoneMockData {
    private YamoneMockData() {}

    public static final class RunningItem {
        public final String title;
        public final String state;
        public final String elapsed;
        public final String keyMetric;

        public RunningItem(String title, String state, String elapsed, String keyMetric) {
            this.title = title;
            this.state = state;
            this.elapsed = elapsed;
            this.keyMetric = keyMetric;
        }
    }

    public static final class HomeSummary {
        public final String activityTime;
        public final String distance;
        public final String calories;
        public final List<RunningItem> runningItems;

        public HomeSummary(String activityTime, String distance, String calories, List<RunningItem> runningItems) {
            this.activityTime = activityTime;
            this.distance = distance;
            this.calories = calories;
            this.runningItems = runningItems;
        }
    }

    public static final class ActivityCardData {
        public final int iconType;
        public final String title;
        public final String subtitle;
        public final String badge;
        public final int tintKind;

        public ActivityCardData(int iconType, String title, String subtitle, String badge, int tintKind) {
            this.iconType = iconType;
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
            this.tintKind = tintKind;
        }
    }

    public static HomeSummary homeSummary() {
        return new HomeSummary(
                "1시간 08분",
                "6.24 km",
                "418 kcal",
                new ArrayList<>()
        );
    }

    public static List<ActivityCardData> activityCards() {
        return Arrays.asList(
                new ActivityCardData(
                        YamoneIconView.ACTIVITY_MULTI,
                        "걷기 · 달리기 · 자전거",
                        "자동으로 활동을 구분해 기록",
                        "자동감지 켜짐",
                        0),
                new ActivityCardData(
                        YamoneIconView.ACTIVITY_SNOW,
                        "Snow",
                        "스키 · 스노보드",
                        "",
                        1),
                new ActivityCardData(
                        YamoneIconView.ACTIVITY_LOCATION,
                        "위치공유",
                        "다른 활동과 동시에 사용할 수 있어요",
                        "",
                        2),
                new ActivityCardData(
                        YamoneIconView.ACTIVITY_SLEEP,
                        "수면",
                        "수면과 코골이 후보를 기록",
                        "",
                        3)
        );
    }
}
