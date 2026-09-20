package com.study.travel_guide.service.growth;

/**
 * 增长运营规则常量：积分分值、等级门槛与称号。
 */
public final class GrowthRule {

    public static final int SIGN_IN = 5;
    public static final int TRIP_GENERATE = 10;
    public static final int CHECK_IN = 5;
    public static final int INVITE = 20;
    public static final int INVITE_REWARD = 10;

    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000};

    public static int levelOf(int growth) {
        if (growth >= 1000) return 5;
        if (growth >= 600) return 4;
        if (growth >= 300) return 3;
        if (growth >= 100) return 2;
        return 1;
    }

    public static String levelTitle(int level) {
        return switch (level) {
            case 5 -> "环球旅者";
            case 4 -> "旅行专家";
            case 3 -> "资深玩家";
            case 2 -> "出行达人";
            default -> "旅行新手";
        };
    }

    public static int nextLevelGrowth(int growth) {
        int level = levelOf(growth);
        if (level >= 5) return 0;
        return LEVEL_THRESHOLDS[level] - growth;
    }

    private GrowthRule() {
    }
}
