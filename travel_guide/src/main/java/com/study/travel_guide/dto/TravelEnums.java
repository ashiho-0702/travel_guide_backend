package com.study.travel_guide.dto;

import java.util.List;
import java.util.Map;

/**
 * 旅行规划输入的枚举常量与中文标签映射。
 */
public final class TravelEnums {

    public static final List<String> PREFERENCES = List.of(
            "nature", "culture", "food", "family", "shopping",
            "photography", "nightlife", "relaxation", "theme_park");

    public static final Map<String, String> PREFERENCE_LABELS = Map.of(
            "nature", "自然风光",
            "culture", "历史人文",
            "food", "美食",
            "family", "亲子",
            "shopping", "购物",
            "photography", "摄影打卡",
            "nightlife", "夜游/夜生活",
            "relaxation", "休闲度假",
            "theme_park", "主题乐园");

    public static final List<String> TRANSPORTATIONS = List.of(
            "walking", "transit", "taxi", "driving", "cycling");

    public static final Map<String, String> TRANSPORTATION_LABELS = Map.of(
            "walking", "步行",
            "transit", "公交/地铁",
            "taxi", "出租车/网约车",
            "driving", "自驾",
            "cycling", "骑行");

    public static final List<String> ENERGY_LEVELS = List.of("easy", "medium", "hard");

    private TravelEnums() {
    }
}
