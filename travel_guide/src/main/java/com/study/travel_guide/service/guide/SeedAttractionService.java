package com.study.travel_guide.service.guide;

import com.study.travel_guide.service.BochaSearchService;
import com.study.travel_guide.service.SearchItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SeedAttractionService {

    private static final List<String> ATTRACTIONS = List.of(
            "西湖", "故宫", "长城", "天坛", "颐和园", "外滩", "兵马俑",
            "桂林漓江", "张家界", "九寨沟", "黄山", "泰山", "布达拉宫", "鼓浪屿");

    private final BochaSearchService bochaSearchService;
    private final AttractionKnowledgeService knowledgeService;

    public SeedAttractionService(BochaSearchService bochaSearchService, AttractionKnowledgeService knowledgeService) {
        this.bochaSearchService = bochaSearchService;
        this.knowledgeService = knowledgeService;
    }

    public Map<String, Object> seed() {
        int success = 0;
        int totalChunks = 0;
        for (String attraction : ATTRACTIONS) {
            try {
                String query = attraction + " 历史 典故 简介 讲解";
                List<SearchItem> results = bochaSearchService.search(query, 5, false);
                if (results.isEmpty()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (SearchItem r : results) {
                    sb.append(r.title()).append('\n').append(r.summary()).append("\n\n");
                }
                knowledgeService.ingest(sb.toString(), attraction);
                success++;
                totalChunks += results.size();
            } catch (Exception e) {
                log.warn("seed attraction {} failed: {}", attraction, e.getMessage());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", ATTRACTIONS.size());
        data.put("success", success);
        data.put("chunks", totalChunks);
        return data;
    }
}
