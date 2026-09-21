package com.study.travel_guide.service.workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.dto.GenerateRequest;
import com.study.travel_guide.dto.TravelEnums;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.TripMapper;
import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.DeepSeekService;
import com.study.travel_guide.service.TencentMapService;
import com.study.travel_guide.service.agent.AgentService;
import com.study.travel_guide.service.memory.UserMemoryService;
import com.study.travel_guide.service.rag.IngestionService;
import com.study.travel_guide.service.rag.RetrievalService;
import com.study.travel_guide.service.growth.GrowthRule;
import com.study.travel_guide.service.growth.PointService;
import com.study.travel_guide.service.rag.RetrievedDoc;
import com.study.travel_guide.service.wechat.SubscribeMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TripWorkflowService {

    private static final String AGENT_SYSTEM_PROMPT = """
            你是旅行规划研究员。你可以调用工具搜集信息：
            - search_web：联网搜索景点/美食/攻略/避坑等真实信息
            - search_kb：检索本地旅行攻略知识库
            - geocode：查询景点经纬度

            重要规则：
            1. 用 1-2 轮搜索获取关键信息（景点、美食、避坑、交通）即可，不要反复搜索同类信息。
            2. 信息足够后立即停止调用工具，直接输出「行程素材汇总」文本。
            3. 「行程素材汇总」应包含：推荐景点、美食、注意事项、通勤建议，并标注信息来源（平台名）。
            """;

    private static final String GUIDE_SYSTEM_PROMPT = """
            你是一位资深的旅行规划师，擅长结合小红书、B站、抖音等平台的真实用户口碑，制定靠谱的行程攻略。

            请严格输出以下结构的 JSON 对象（只输出 JSON，不要任何额外文字或 markdown 代码块）：
            {
              "city": "城市名",
              "overview": "整体行程概览与建议（2-3句）",
              "days": [
                {
                  "day": 1,
                  "title": "当天主题标题",
                  "theme": "主题标签",
                  "spots": [
                    {"name": "景点名", "reason": "推荐理由", "tip": "避坑提示", "duration": "游玩时长", "transport": "到达方式"}
                  ],
                  "food": [{"name": "美食名", "reason": "推荐理由"}],
                  "note": "当天注意事项"
                }
              ],
              "tips": ["整体避坑建议"],
              "sources": [{"title": "来源标题", "url": "链接", "site": "平台名"}]
            }

            要求：
            1. days 数组长度必须等于用户填写的天数。
            2. 每天 3-5 个景点，同一片区域、路线顺路。
            3. 结合体力档位调整强度。
            4. 根据总预算合理安排住宿/餐饮/门票，不超出预算。
            5. reason/tip 优先引用搜集到的真实信息。
            6. 结合用户历史偏好和其他需求做个性化推荐。
            """;

    private static final String REFLECT_SYSTEM_PROMPT = "你是严格的旅行攻略评审专家。";

    private final RetrievalService retrievalService;
    private final AgentService agentService;
    private final DeepSeekService deepSeekService;
    private final TencentMapService tencentMapService;
    private final IngestionService ingestionService;
    private final UserMemoryService userMemoryService;
    private final TripMapper tripMapper;
    private final UserMapper userMapper;
    private final SubscribeMessageService subscribeMessageService;
    private final PointService pointService;
    private final ExecutorService taskExecutor;

    @Value("${workflow.reflect:true}")
    private boolean reflectEnabled;

    public TripWorkflowService(RetrievalService retrievalService,
                               AgentService agentService,
                               DeepSeekService deepSeekService,
                               TencentMapService tencentMapService,
                               IngestionService ingestionService,
                               UserMemoryService userMemoryService,
                               TripMapper tripMapper,
                               UserMapper userMapper,
                               SubscribeMessageService subscribeMessageService,
                               PointService pointService,
                               ExecutorService taskExecutor) {
        this.retrievalService = retrievalService;
        this.agentService = agentService;
        this.deepSeekService = deepSeekService;
        this.tencentMapService = tencentMapService;
        this.ingestionService = ingestionService;
        this.userMemoryService = userMemoryService;
        this.tripMapper = tripMapper;
        this.userMapper = userMapper;
        this.subscribeMessageService = subscribeMessageService;
        this.pointService = pointService;
        this.taskExecutor = taskExecutor;
    }

    public Map<String, Object> generate(Long userId, GenerateRequest req) {
        log.info("[workflow] 1/7 输入校验");
        validate(req);

        String memory = userMemoryService.buildMemoryContext(userId, 5);

        log.info("[workflow] 2/7 RAG 检索知识库");
        String kbContext = retrieveKnowledge(req);

        log.info("[workflow] 3/7 Agent 工具调用");
        String research = agentService.run(AGENT_SYSTEM_PROMPT, buildAgentQuery(req, kbContext, memory), 6);

        log.info("[workflow] 4/7 生成攻略");
        JsonNode guide = deepSeekService.generateJson(GUIDE_SYSTEM_PROMPT, buildGeneratePrompt(req, research, memory));

        log.info("[workflow] 5/7 反思校验");
        guide = reflectAndRefine(req, guide);

        log.info("[workflow] 6/7 并行地理编码");
        fillCoordinates(guide, req.getCity());

        log.info("[workflow] 7/7 持久化 + RAG 摄入");
        Trip trip = persist(userId, req, guide);
        ingestKnowledge(guide, req.getCity());
        awardTripPoints(userId);

        taskExecutor.execute(() -> sendSubscribeMessage(userId, req, trip));

        Map<String, Object> data = new HashMap<>();
        data.put("tripId", trip.getId());
        data.put("result", guide);
        return data;
    }

    private void sendSubscribeMessage(Long userId, GenerateRequest req, Trip trip) {
        try {
            User user = userMapper.findById(userId);
            if (user != null && user.getOpenid() != null && !user.getOpenid().isBlank()) {
                subscribeMessageService.sendGenerateDone(user.getOpenid(), req.getCity(), req.getDays(), trip.getId());
            }
        } catch (Exception e) {
            log.warn("订阅消息发送失败: {}", e.getMessage());
        }
    }

    private void awardTripPoints(Long userId) {
        try {
            pointService.addPoints(userId, "trip_generate", GrowthRule.TRIP_GENERATE, "生成攻略");
        } catch (Exception e) {
            log.warn("生成攻略加分失败: {}", e.getMessage());
        }
    }

    public void generateStreaming(Long userId, GenerateRequest req, SseEmitter emitter) {
        try {
            sendEvent(emitter, "step", "开始规划");
            validate(req);

            String memory = userMemoryService.buildMemoryContext(userId, 5);

            sendEvent(emitter, "step", "检索知识库");
            String kbContext = retrieveKnowledge(req);

            sendEvent(emitter, "step", "智能体搜集信息");
            String research = agentService.run(AGENT_SYSTEM_PROMPT, buildAgentQuery(req, kbContext, memory), 6,
                    progress -> sendEvent(emitter, "step", progress));

            sendEvent(emitter, "step", "生成攻略");
            JsonNode guide = deepSeekService.streamGenerateJson(
                    GUIDE_SYSTEM_PROMPT,
                    buildGeneratePrompt(req, research, memory),
                    token -> sendEvent(emitter, "token", token)
            );

            sendEvent(emitter, "step", "反思校验");
            guide = reflectAndRefine(req, guide);

            sendEvent(emitter, "step", "补齐坐标");
            fillCoordinates(guide, req.getCity());

            sendEvent(emitter, "step", "保存并沉淀");
            Trip trip = persist(userId, req, guide);
            ingestKnowledge(guide, req.getCity());
            awardTripPoints(userId);

            sendEvent(emitter, "done", Map.of("tripId", trip.getId()));
            emitter.complete();
        } catch (ClientDisconnected e) {
            log.info("客户端断开连接，中止生成: userId={}", userId);
        } catch (Exception e) {
            log.error("streaming generate failed", e);
            try {
                emitter.send(SseEmitter.event().data(Map.of("type", "error", "data", e.getMessage())));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    private void validate(GenerateRequest req) {
        if (req.getCity() == null || req.getCity().isBlank()) {
            throw new BizException("目的地城市不能为空");
        }
        if (req.getDays() == null || req.getDays() < 1 || req.getDays() > 15) {
            throw new BizException("天数需在 1-15 之间");
        }
        if (req.getPeopleCount() != null && req.getPeopleCount() < 1) {
            throw new BizException("人数需大于 0");
        }
        if (req.getEnergyLevel() != null && !TravelEnums.ENERGY_LEVELS.contains(req.getEnergyLevel())) {
            throw new BizException("体力档位不合法");
        }
        if (req.getPreferences() != null) {
            for (String p : req.getPreferences()) {
                if (p == null || !TravelEnums.PREFERENCES.contains(p)) {
                    throw new BizException("兴趣偏好不合法: " + p);
                }
            }
        }
        if (req.getTransportation() == null || req.getTransportation().isEmpty()) {
            throw new BizException("交通方式至少选择一项");
        }
        for (String t : req.getTransportation()) {
            if (t == null || !TravelEnums.TRANSPORTATIONS.contains(t)) {
                throw new BizException("交通方式不合法: " + t);
            }
        }
    }

    private String retrieveKnowledge(GenerateRequest req) {
        String query = buildQuery(req);
        log.info("[rag] 检索知识库，query={}, city={}", query, req.getCity());
        try {
            List<RetrievedDoc> docs = retrievalService.retrieve(query, 3, req.getCity());
            if (docs.isEmpty()) {
                log.info("[rag] 知识库无相关内容（首次使用或与历史内容不相似，属正常）");
                return "";
            }
            for (int i = 0; i < docs.size(); i++) {
                RetrievedDoc d = docs.get(i);
                String preview = d.text();
                if (preview.length() > 100) {
                    preview = preview.substring(0, 100) + "...";
                }
                log.info("[rag] 命中 {}：score={}，内容预览：{}", i + 1, String.format("%.4f", d.score()), preview);
            }
            return docs.stream().map(RetrievedDoc::text).collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.warn("[rag] 检索失败，降级为无知识库生成: {}", e.getMessage());
            return "";
        }
    }

    private JsonNode reflectAndRefine(GenerateRequest req, JsonNode guide) {
        if (!reflectEnabled) {
            return guide;
        }
        String userPrompt = "用户需求：城市=" + req.getCity() + "，天数=" + req.getDays()
                + "，体力=" + energyLabel(req.getEnergyLevel()) + "，预算=" + (req.getBudget() == null ? "不限" : req.getBudget())
                + "\n\n当前攻略 JSON：\n" + guide.toString()
                + "\n\n请检查路线是否顺路、预算是否合理、景点强度是否匹配体力档位。若有明显问题，输出改进后的完整 JSON；若无需改进，原样输出该 JSON。只输出 JSON。";
        try {
            return deepSeekService.generateJson(REFLECT_SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.warn("reflect failed, keep original: {}", e.getMessage());
            return guide;
        }
    }

    private void fillCoordinates(JsonNode guide, String city) {
        JsonNode days = guide.get("days");
        if (days == null || !days.isArray()) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (JsonNode day : days) {
            JsonNode spots = day.get("spots");
            if (spots == null || !spots.isArray()) {
                continue;
            }
            for (JsonNode spot : spots) {
                if (!(spot instanceof ObjectNode obj)) {
                    continue;
                }
                String name = obj.path("name").asText();
                if (name.isBlank()) {
                    continue;
                }
                futures.add(CompletableFuture.runAsync(() -> {
                    double[] coord = tencentMapService.searchLocation(name, city);
                    if (coord != null) {
                        obj.put("lat", coord[0]);
                        obj.put("lng", coord[1]);
                    }
                }, taskExecutor));
            }
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    private Trip persist(Long userId, GenerateRequest req, JsonNode guide) {
        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setCity(req.getCity());
        trip.setStartDate(req.getStartDate());
        trip.setPreferences(joinPreferences(req.getPreferences()));
        trip.setBudget(req.getBudget());
        trip.setDays(req.getDays());
        trip.setPeopleCount(req.getPeopleCount());
        trip.setEnergyLevel(req.getEnergyLevel());
        trip.setTransportation(joinTransportation(req.getTransportation()));
        trip.setExtraRequirements(req.getExtraRequirements());
        trip.setStatus("done");
        trip.setResult(guide.toString());
        tripMapper.insert(trip);
        return trip;
    }

    private void ingestKnowledge(JsonNode guide, String city) {
        try {
            ingestionService.ingest(guideToText(guide), city);
        } catch (Exception e) {
            log.warn("RAG ingest failed: {}", e.getMessage());
        }
    }

    private String guideToText(JsonNode guide) {
        StringBuilder sb = new StringBuilder();
        sb.append("城市：").append(guide.path("city").asText()).append('\n');
        sb.append("概览：").append(guide.path("overview").asText()).append('\n');
        for (JsonNode day : guide.path("days")) {
            sb.append("\n第 ").append(day.path("day").asInt()).append(" 天：").append(day.path("title").asText()).append('\n');
            for (JsonNode spot : day.path("spots")) {
                sb.append("- 景点 ").append(spot.path("name").asText())
                        .append("：").append(spot.path("reason").asText()).append('\n');
            }
            for (JsonNode food : day.path("food")) {
                sb.append("- 美食 ").append(food.path("name").asText())
                        .append("：").append(food.path("reason").asText()).append('\n');
            }
        }
        return sb.toString();
    }

    private void sendEvent(SseEmitter emitter, String type, Object data) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", type, "data", data)));
        } catch (Exception e) {
            throw new ClientDisconnected(e);
        }
    }

    private String buildQuery(GenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(req.getCity()).append(' ').append(req.getDays()).append("天 旅游攻略 景点 美食");
        if (req.getPreferences() != null && !req.getPreferences().isEmpty()) {
            sb.append(' ').append(joinPreferenceLabels(req.getPreferences()));
        }
        return sb.toString();
    }

    private String buildAgentQuery(GenerateRequest req, String kbContext, String memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为以下旅行需求搜集素材：\n");
        sb.append("- 目的地城市：").append(req.getCity()).append('\n');
        if (req.getStartDate() != null && !req.getStartDate().isBlank()) {
            sb.append("- 开始日期：").append(req.getStartDate()).append('\n');
        }
        sb.append("- 天数：").append(req.getDays()).append("天\n");
        if (req.getPeopleCount() != null) {
            sb.append("- 人数：").append(req.getPeopleCount()).append("人\n");
        }
        sb.append("- 兴趣偏好：").append(joinPreferenceLabels(req.getPreferences())).append('\n');
        sb.append("- 体力：").append(energyLabel(req.getEnergyLevel())).append('\n');
        if (req.getBudget() != null && !req.getBudget().isBlank()) {
            sb.append("- 总预算：").append(req.getBudget()).append('\n');
        }
        sb.append("- 主要交通方式：").append(joinTransportationLabels(req.getTransportation())).append('\n');
        if (req.getExtraRequirements() != null && !req.getExtraRequirements().isBlank()) {
            sb.append("- 其他需求：").append(req.getExtraRequirements()).append('\n');
        }
        if (memory != null && !memory.isBlank()) {
            sb.append('\n').append(memory).append('\n');
        }
        if (kbContext != null && !kbContext.isBlank()) {
            sb.append("\n知识库已有相关素材：\n").append(kbContext).append('\n');
        }
        return sb.toString();
    }

    private String buildGeneratePrompt(GenerateRequest req, String research, String memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户需求：\n");
        sb.append("- 目的地城市：").append(req.getCity()).append('\n');
        if (req.getStartDate() != null && !req.getStartDate().isBlank()) {
            sb.append("- 开始日期：").append(req.getStartDate()).append('\n');
        }
        sb.append("- 天数：").append(req.getDays()).append("天\n");
        if (req.getPeopleCount() != null) {
            sb.append("- 人数：").append(req.getPeopleCount()).append("人\n");
        }
        sb.append("- 兴趣偏好：").append(joinPreferenceLabels(req.getPreferences())).append('\n');
        sb.append("- 总预算：").append(req.getBudget() == null ? "不限" : req.getBudget()).append('\n');
        sb.append("- 体力：").append(energyLabel(req.getEnergyLevel())).append('\n');
        sb.append("- 主要交通方式：").append(joinTransportationLabels(req.getTransportation())).append('\n');
        if (req.getExtraRequirements() != null && !req.getExtraRequirements().isBlank()) {
            sb.append("- 其他需求：").append(req.getExtraRequirements()).append('\n');
        }
        if (memory != null && !memory.isBlank()) {
            sb.append('\n').append(memory).append('\n');
        }
        sb.append("\n以下是搜集到的行程素材：\n").append(research);
        sb.append("\n请输出 JSON。");
        return sb.toString();
    }

    private String energyLabel(String level) {
        if (level == null) {
            return "适中";
        }
        return switch (level) {
            case "easy" -> "轻松（减少点位和步行距离，预留休息，适合老人或儿童）";
            case "hard" -> "充沛（可安排更多点位，但仍需满足合理开放时间和交通时间）";
            default -> "适中（默认强度，兼顾游览密度和休息）";
        };
    }

    private String joinPreferences(List<String> preferences) {
        return preferences == null || preferences.isEmpty()
                ? null
                : String.join(",", new LinkedHashSet<>(preferences));
    }

    private String joinTransportation(List<String> transportation) {
        return transportation == null || transportation.isEmpty()
                ? null
                : String.join(",", new LinkedHashSet<>(transportation));
    }

    private String joinPreferenceLabels(List<String> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return "不限";
        }
        return new LinkedHashSet<>(preferences).stream()
                .map(TravelEnums.PREFERENCE_LABELS::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、"));
    }

    private String joinTransportationLabels(List<String> transportation) {
        if (transportation == null || transportation.isEmpty()) {
            return "不限";
        }
        return new LinkedHashSet<>(transportation).stream()
                .map(TravelEnums.TRANSPORTATION_LABELS::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、"));
    }

    private static class ClientDisconnected extends RuntimeException {
        ClientDisconnected(Throwable cause) {
            super(cause);
        }
    }
}
