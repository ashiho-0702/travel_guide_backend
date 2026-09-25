package com.study.travel_guide.service.wechat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 内容安全：调用微信 msg_sec_check 检测用户输入文本。
 * 违规（risky）抛异常拒绝；疑似（review）放行并记日志；接口异常时放行（不阻断核心流程）。
 */
@Slf4j
@Service
public class ContentSecurityService {

    public static final int SCENE_PROFILE = 1;   // 资料（昵称）
    public static final int SCENE_COMMENT = 2;  // 评论/输入

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final WechatAccessTokenService accessTokenService;
    private final UserMapper userMapper;

    public ContentSecurityService(RestClient restClient, JsonMapper jsonMapper,
                                  WechatAccessTokenService accessTokenService, UserMapper userMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.accessTokenService = accessTokenService;
        this.userMapper = userMapper;
    }

    public void checkText(Long userId, String content, int scene) {
        if (content == null || content.isBlank()) {
            return;
        }
        String openid = null;
        User user = userMapper.findById(userId);
        if (user != null) {
            openid = user.getOpenid();
        }
        String token = accessTokenService.getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token={token}";

        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("version", 2);
        body.put("scene", scene);
        if (openid != null) {
            body.put("openid", openid);
        }

        try {
            String json = restClient.post()
                    .uri(url, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = jsonMapper.readTree(json);
            int errcode = root.path("errcode").asInt(-1);
            if (errcode != 0) {
                log.warn("内容安全接口异常: errcode={}, errmsg={}", errcode, root.path("errmsg").asText());
                return;
            }
            String suggest = root.path("result").path("suggest").asText();
            if ("risky".equals(suggest)) {
                throw new BizException(400, "内容包含违规信息，请修改后重试");
            }
            if ("review".equals(suggest)) {
                log.warn("内容疑似违规(review): {}", content);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("内容安全检测失败，放行: {}", e.getMessage());
        }
    }
}
