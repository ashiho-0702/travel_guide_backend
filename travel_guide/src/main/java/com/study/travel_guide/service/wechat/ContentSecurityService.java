package com.study.travel_guide.service.wechat;

import tools.jackson.databind.JsonNode;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.service.DeepSeekService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 内容安全：用 DeepSeek 大模型审核用户输入文本（替代微信 msg_sec_check，个人主体无权限）。
 * 违规（risky）抛异常拒绝；接口异常时放行（不阻断核心流程）。
 */
@Slf4j
@Service
public class ContentSecurityService {

    public static final int SCENE_PROFILE = 1;   // 资料（昵称）
    public static final int SCENE_COMMENT = 2;   // 评论/输入（备注/重排指令）
    public static final int SCENE_CHAT = 3;      // 语音问答（跳过审核，避免延迟）

    private static final String AUDIT_SYSTEM_PROMPT = """
            你是内容安全审核员。判断用户输入文本是否违规，违规类型包括：
            1. 色情、低俗、性暗示
            2. 政治敏感、反动言论
            3. 辱骂、人身攻击、歧视
            4. 违法信息（赌博、毒品、诈骗、暴力等）
            5. 广告、垃圾信息、引流

            只输出 JSON：{"suggest": "risky" 或 "pass", "reason": "违规原因（pass 时为空）"}
            """;

    private final DeepSeekService deepSeekService;

    public ContentSecurityService(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    public void checkText(Long userId, String content, int scene) {
        if (scene == SCENE_CHAT) {
            return;
        }
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            JsonNode result = deepSeekService.generateJson(AUDIT_SYSTEM_PROMPT, content);
            if ("risky".equals(result.path("suggest").asText())) {
                throw new BizException(400, "内容包含违规信息，请修改后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("内容安全检测失败，放行: {}", e.getMessage());
        }
    }
}
