package com.study.travel_guide.service.guide;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.service.BochaSearchService;
import com.study.travel_guide.service.DeepSeekService;
import com.study.travel_guide.service.SearchItem;
import com.study.travel_guide.service.rag.RetrievedDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationService {

    private static final String SYSTEM_PROMPT = """
            你是一位亲切的旅行语音导游助手，能用简洁、口语化的方式回答游客的问题。
            结合历史对话上下文和提供的素材回答，回答控制在 100-200 字，适合语音播报，不要有任何 markdown 或特殊符号。

            只输出 JSON：{"answer": "回答内容"}
            """;

    private static final String KEY_PREFIX = "guide:session:";
    private static final int MAX_HISTORY = 10;
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final AttractionKnowledgeService knowledgeService;
    private final BochaSearchService bochaSearchService;
    private final DeepSeekService deepSeekService;

    public ConversationService(StringRedisTemplate redisTemplate,
                               JsonMapper jsonMapper,
                               AttractionKnowledgeService knowledgeService,
                               BochaSearchService bochaSearchService,
                               DeepSeekService deepSeekService) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.knowledgeService = knowledgeService;
        this.bochaSearchService = bochaSearchService;
        this.deepSeekService = deepSeekService;
    }

    public Map<String, Object> chat(String sessionId, String question) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        List<Map<String, String>> history = loadHistory(sessionId);

        String kbContext = "";
        try {
            List<RetrievedDoc> docs = knowledgeService.search(question, null, 3);
            if (!docs.isEmpty()) {
                kbContext = docs.stream().map(RetrievedDoc::text).collect(Collectors.joining("\n---\n"));
            }
        } catch (Exception e) {
            log.warn("chat KB search failed: {}", e.getMessage());
        }

        String webContext = "";
        try {
            List<SearchItem> results = bochaSearchService.search(question, 5, false);
            if (!results.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (SearchItem r : results) {
                    sb.append(r.title()).append('\n').append(r.summary()).append("\n\n");
                }
                webContext = sb.toString();
            }
        } catch (Exception e) {
            log.warn("chat web search failed: {}", e.getMessage());
        }

        StringBuilder context = new StringBuilder();
        for (Map<String, String> msg : history) {
            context.append(msg.get("role")).append('：').append(msg.get("content")).append('\n');
        }

        String userPrompt = "历史对话：\n" + context
                + "\n当前问题：" + question
                + "\n\n知识库素材：\n" + kbContext
                + "\n\n联网搜索素材：\n" + webContext
                + "\n\n请回答。";

        JsonNode result = deepSeekService.generateJson(SYSTEM_PROMPT, userPrompt);
        String answer = result.path("answer").asText();

        history.add(Map.of("role", "user", "content", question));
        history.add(Map.of("role", "assistant", "content", answer));
        if (history.size() > MAX_HISTORY) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY, history.size()));
        }
        saveHistory(sessionId, history);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("answer", answer);
        return data;
    }

    private List<Map<String, String>> loadHistory(String sessionId) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("读取会话历史失败: {}", e.getMessage());
            return new ArrayList<>();
        }
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = jsonMapper.readTree(json);
            List<Map<String, String>> history = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    Map<String, String> msg = new HashMap<>();
                    msg.put("role", item.path("role").asText());
                    msg.put("content", item.path("content").asText());
                    history.add(msg);
                }
            }
            return history;
        } catch (Exception e) {
            log.warn("解析会话历史失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveHistory(String sessionId, List<Map<String, String>> history) {
        try {
            String json = jsonMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, json, SESSION_TTL);
        } catch (Exception e) {
            log.warn("保存会话历史失败: {}", e.getMessage());
        }
    }
}
