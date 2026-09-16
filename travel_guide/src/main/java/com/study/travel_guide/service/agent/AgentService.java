package com.study.travel_guide.service.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.service.DeepSeekService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class AgentService {

    private final DeepSeekService deepSeekService;
    private final ToolRegistry toolRegistry;
    private final JsonMapper jsonMapper;

    public AgentService(DeepSeekService deepSeekService, ToolRegistry toolRegistry, JsonMapper jsonMapper) {
        this.deepSeekService = deepSeekService;
        this.toolRegistry = toolRegistry;
        this.jsonMapper = jsonMapper;
    }

    public String run(String systemPrompt, String userQuery, int maxIterations) {
        return run(systemPrompt, userQuery, maxIterations, null);
    }

    public String run(String systemPrompt, String userQuery, int maxIterations, Consumer<String> onProgress) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userQuery));
        List<Map<String, Object>> tools = toolRegistry.buildToolDefinitions();

        for (int i = 0; i < maxIterations; i++) {
            log.info("[agent] 第 {}/{} 轮：调用 DeepSeek", i + 1, maxIterations);
            JsonNode response = deepSeekService.chat(messages, tools);
            JsonNode message = response.path("choices").get(0).path("message");
            JsonNode toolCalls = message.get("tool_calls");

            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info("[agent] DeepSeek 返回最终结果，结束");
                return message.path("content").asText();
            }

            log.info("[agent] 模型请求调用 {} 个工具", toolCalls.size());

            @SuppressWarnings("unchecked")
            Map<String, Object> assistantMsg = (Map<String, Object>) jsonMapper.convertValue(message, Map.class);
            messages.add(assistantMsg);

            for (JsonNode call : toolCalls) {
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String argsJson = call.path("function").path("arguments").asText();
                Map<String, Object> args = parseArgs(argsJson);

                log.info("[agent] 执行工具 {}: {}", name, argsJson);
                if (onProgress != null) {
                    onProgress.accept(formatProgress(name, args));
                }

                String result = executeTool(name, args);
                log.info("[agent] 工具 {} 执行完成", name);
                messages.add(Map.of("role", "tool", "tool_call_id", id, "content", result));
            }
        }
        log.warn("[agent] 达到最大迭代次数 {} 仍未产出最终结果", maxIterations);
        throw new BizException(500, "Agent 达到最大迭代次数(" + maxIterations + ")仍未完成，请重试");
    }

    private Map<String, Object> parseArgs(String argsJson) {
        try {
            JsonNode argsNode = jsonMapper.readTree(argsJson);
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) jsonMapper.convertValue(argsNode, Map.class);
            return args == null ? Map.of() : args;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String executeTool(String name, Map<String, Object> args) {
        Tool tool = toolRegistry.get(name);
        if (tool == null) {
            return "错误：未知工具 " + name;
        }
        try {
            return tool.execute(args);
        } catch (Exception e) {
            return "工具执行失败(" + name + "): " + e.getMessage();
        }
    }

    private String formatProgress(String name, Map<String, Object> args) {
        return switch (name) {
            case "search_web" -> "正在搜索：" + args.get("query");
            case "search_kb" -> "检索知识库：" + args.get("query");
            case "geocode" -> "查询坐标：" + args.get("name");
            default -> "调用工具：" + name;
        };
    }
}
