package com.study.travel_guide.service.growth;

import java.util.List;
import java.util.Map;

/**
 * 增长运营规则常量：积分分值、等级门槛与称号、会员权益、连续签到奖励、可兑换项。
 */
public final class GrowthRule {

    public static final int SIGN_IN = 5;
    public static final int TRIP_GENERATE = 10;
    public static final int CHECK_IN = 5;
    public static final int INVITE = 20;
    public static final int INVITE_REWARD = 10;

    public static final int STREAK_BONUS_7 = 10;
    public static final int STREAK_BONUS_30 = 50;

    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000};

    private static final Map<Integer, List<String>> BENEFITS = Map.of(
            1, List.of("基础攻略生成", "历史行程保存"),
            2, List.of("历史行程容量 +10", "专属称号「出行达人」"),
            3, List.of("历史行程容量 +20", "专属称号「资深玩家」", "解锁更多主题"),
            4, List.of("历史行程容量 +30", "专属称号「旅行专家」", "解锁更多主题"),
            5, List.of("历史行程容量 +50", "专属称号「环球旅者」", "全部主题解锁")
    );

    // 可兑换项：itemId → 积分价
    public static final Map<String, Integer> REDEEM_COSTS = Map.of(
            "pdf_export", 50,
            "theme_dark", 100,
            "badge_explorer", 200
    );

    // 可兑换项：itemId → 名称
    public static final Map<String, String> REDEEM_NAMES = Map.of(
            "pdf_export", "导出 PDF 次数 +1",
            "theme_dark", "暗黑主题",
            "badge_explorer", "探险家徽章"
    );

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

    public static List<String> benefitsOf(int level) {
        return BENEFITS.getOrDefault(level, List.of());
    }

    public static int streakBonus(int streak) {
        if (streak >= 30) return STREAK_BONUS_30;
        if (streak >= 7) return STREAK_BONUS_7;
        return 0;
    }

    private GrowthRule() {
    }
}
