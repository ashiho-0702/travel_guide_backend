package com.study.travel_guide.service.guide;

import tools.jackson.databind.JsonNode;
import com.study.travel_guide.service.BochaSearchService;
import com.study.travel_guide.service.DeepSeekService;
import com.study.travel_guide.service.SearchItem;
import com.study.travel_guide.service.rag.RetrievedDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TourGuideService {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的语音导游，擅长用生动、口语化的语言介绍景点。
            请根据提供的素材，写一段适合语音播报的讲解词（200-400字），
            内容包含：景点历史/典故、主要看点、游览建议。语气亲切自然，不要有任何 markdown 或特殊符号。

            只输出 JSON：{"script": "讲解词内容"}
            """;

    private final AttractionKnowledgeService knowledgeService;
    private final BochaSearchService bochaSearchService;
    private final DeepSeekService deepSeekService;

    public TourGuideService(AttractionKnowledgeService knowledgeService,
                            BochaSearchService bochaSearchService,
                            DeepSeekService deepSeekService) {
        this.knowledgeService = knowledgeService;
        this.bochaSearchService = bochaSearchService;
        this.deepSeekService = deepSeekService;
    }

    public Map<String, Object> narrate(String attraction) {
        String kbContext = "";
        try {
            List<RetrievedDoc> docs = knowledgeService.search(attraction, attraction, 3);
            if (!docs.isEmpty()) {
                kbContext = docs.stream().map(RetrievedDoc::text).collect(Collectors.joining("\n---\n"));
                String first = docs.get(0).text();
                if (first.length() > 50) {
                    first = first.substring(0, 50) + "...";
                }
                log.info("[guide] 讲解库命中 {} 条，第 1 条：{}", docs.size(), first);
            } else {
                log.info("[guide] 讲解库无命中（首次使用或该景点还没灌种子）");
            }
        } catch (Exception e) {
            log.warn("attraction KB search failed: {}", e.getMessage());
        }

        String webContext = "";
        try {
            List<SearchItem> results = bochaSearchService.search(attraction + " 历史 典故 简介", 5, false);
            if (!results.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (SearchItem r : results) {
                    sb.append(r.title()).append('\n').append(r.summary()).append("\n\n");
                }
                webContext = sb.toString();
            }
        } catch (Exception e) {
            log.warn("attraction web search failed: {}", e.getMessage());
        }

        String userPrompt = "景点：" + attraction
                + "\n\n知识库素材：\n" + kbContext
                + "\n\n联网搜索素材：\n" + webContext
                + "\n\n请生成讲解词。";

        JsonNode result = deepSeekService.generateJson(SYSTEM_PROMPT, userPrompt);
        String script = result.path("script").asText();

        Map<String, Object> data = new HashMap<>();
        data.put("attraction", attraction);
        data.put("script", script);
        return data;
    }
}
