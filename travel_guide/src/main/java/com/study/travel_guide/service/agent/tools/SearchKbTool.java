package com.study.travel_guide.service.agent.tools;

import com.study.travel_guide.service.agent.Tool;
import com.study.travel_guide.service.rag.RetrievalService;
import com.study.travel_guide.service.rag.RetrievedDoc;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchKbTool implements Tool {

    private final RetrievalService retrievalService;

    public SearchKbTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return "search_kb";
    }

    @Override
    public String description() {
        return "检索本地旅行攻略知识库（历史优质攻略内容）";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "检索问题，如「上海有哪些必去景点」"),
                        "city", Map.of("type", "string", "description", "目标城市（用于按城市过滤，可选）")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        String city = (String) args.get("city");
        List<RetrievedDoc> docs = retrievalService.retrieve(query, 5, city);
        if (docs.isEmpty()) {
            return "知识库暂无相关内容";
        }
        StringBuilder sb = new StringBuilder();
        for (RetrievedDoc d : docs) {
            sb.append(d.text()).append("\n\n");
        }
        return sb.toString();
    }
}
