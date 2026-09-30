package com.study.travel_guide.service.guide;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.entity.Expense;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.service.ExpenseService;
import com.study.travel_guide.service.TripService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搭子记忆摘要：从某份行程 + 记账生成精简文本，作为搭子的「记忆」注入对话上下文。
 */
@Slf4j
@Service
public class BuddyMemoryService {

    private final TripService tripService;
    private final ExpenseService expenseService;
    private final JsonMapper jsonMapper;

    public BuddyMemoryService(TripService tripService, ExpenseService expenseService, JsonMapper jsonMapper) {
        this.tripService = tripService;
        this.expenseService = expenseService;
        this.jsonMapper = jsonMapper;
    }

    public String buildMemory(Long userId, Long tripId) {
        Trip trip = tripService.detailAsMember(userId, tripId);
        StringBuilder sb = new StringBuilder();
        sb.append("你正在陪用户看这份攻略：").append(trip.getCity())
                .append(" ").append(trip.getDays()).append(" 天\n");

        appendTripSummary(sb, trip.getResult());
        appendExpenseSummary(sb, userId, tripId);
        return sb.toString();
    }

    private void appendTripSummary(StringBuilder sb, String result) {
        JsonNode guide = parseResult(result);
        if (guide == null) {
            sb.append("（暂无行程内容）\n");
            return;
        }
        for (JsonNode day : guide.path("days")) {
            sb.append("第").append(day.path("day").asText()).append("天");
            String title = day.path("title").asText();
            if (title != null && !title.isBlank()) {
                sb.append("「").append(title).append("」");
            }
            sb.append("：");
            List<String> spotDesc = new ArrayList<>();
            JsonNode spots = day.path("spots");
            if (spots.isArray()) {
                for (JsonNode spot : spots) {
                    String name = spot.path("name").asText();
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    StringBuilder d = new StringBuilder(name);
                    String duration = spot.path("duration").asText();
                    if (duration != null && !duration.isBlank()) {
                        d.append("(").append(duration).append(")");
                    }
                    JsonNode practical = spot.path("practical");
                    String booking = practical.path("booking").asText();
                    String hours = practical.path("hours").asText();
                    if ((booking != null && !booking.isBlank()) || (hours != null && !hours.isBlank())) {
                        d.append("[");
                        if (booking != null && !booking.isBlank()) {
                            d.append("预约:").append(booking);
                        }
                        if (hours != null && !hours.isBlank()) {
                            d.append(" 时间:").append(hours);
                        }
                        d.append("]");
                    }
                    spotDesc.add(d.toString());
                }
            }
            sb.append(spotDesc.isEmpty() ? "（无景点）" : String.join("、", spotDesc)).append("\n");
        }
    }

    private void appendExpenseSummary(StringBuilder sb, Long userId, Long tripId) {
        List<Expense> expenses = expenseService.list(userId, tripId);
        if (expenses == null || expenses.isEmpty()) {
            sb.append("记账：暂无支出记录\n");
            return;
        }
        Map<String, Object> summary = expenseService.summary(userId, tripId);
        sb.append("记账：总花费 ").append(summary.get("total")).append(" 元，明细：");
        for (Expense e : expenses) {
            sb.append(categoryLabel(e.getCategory())).append(" ").append(e.getAmount()).append(" 元");
            if (e.getNote() != null && !e.getNote().isBlank()) {
                sb.append("(").append(e.getNote()).append(")");
            }
            sb.append("、");
        }
        sb.setLength(sb.length() - 1);
        sb.append("\n");
    }

    private String categoryLabel(String category) {
        if (category == null) {
            return "";
        }
        return switch (category) {
            case "accommodation" -> "住宿";
            case "transport" -> "交通";
            case "food" -> "餐饮";
            case "misc" -> "临时";
            default -> category;
        };
    }

    private JsonNode parseResult(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readTree(result);
        } catch (Exception e) {
            log.warn("[buddy] 解析攻略失败: {}", e.getMessage());
            return null;
        }
    }
}
