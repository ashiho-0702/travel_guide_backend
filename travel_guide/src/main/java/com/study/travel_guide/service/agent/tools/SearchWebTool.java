package com.study.travel_guide.service.agent.tools;

import com.study.travel_guide.service.BochaSearchService;
import com.study.travel_guide.service.SearchItem;
import com.study.travel_guide.service.agent.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchWebTool implements Tool {

    private final BochaSearchService bochaSearchService;

    public SearchWebTool(BochaSearchService bochaSearchService) {
        this.bochaSearchService = bochaSearchService;
    }

    @Override
    public String name() {
        return "search_web";
    }

    @Override
    public String description() {
        return "联网搜索旅游攻略、景点、美食等真实信息（来自小红书/B站/微博等）";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索关键词，如「上海 3天 攻略 景点」")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        List<SearchItem> results = bochaSearchService.search(query, 5);
        if (results.isEmpty()) {
            return "未搜到相关结果";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchItem r : results) {
            sb.append("【").append(r.site() == null ? "来源" : r.site()).append("】")
                    .append(r.title()).append('\n')
                    .append(r.summary()).append("\n\n");
        }
        return sb.toString();
    }
}
