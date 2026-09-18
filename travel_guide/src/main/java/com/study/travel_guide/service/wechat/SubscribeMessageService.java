package com.study.travel_guide.service.wechat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SubscribeMessageService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final WechatAccessTokenService accessTokenService;

    @Value("${wechat.subscribe-template-id:}")
    private String templateId;

    public SubscribeMessageService(RestClient restClient, JsonMapper jsonMapper, WechatAccessTokenService accessTokenService) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.accessTokenService = accessTokenService;
    }

    public void sendGenerateDone(String openid, String city, Integer days, Long tripId) {
        if (templateId == null || templateId.isBlank()) {
            log.warn("订阅消息模板 ID 未配置，跳过发送");
            return;
        }
        // 模板字段：行程名称=thing26、行程日期=date14、行程路线=thing20、备注=thing21
        Map<String, Object> data = new HashMap<>();
        data.put("thing26", Map.of("value", city + " " + days + "天攻略"));    // 行程名称
        data.put("date14", Map.of("value", LocalDate.now().toString()));       // 行程日期
        data.put("thing20", Map.of("value", "已生成每日行程和景点美食推荐"));   // 行程路线
        data.put("thing21", Map.of("value", "点击查看攻略详情"));               // 备注

        send(openid, data, "pages/result/result?tripId=" + tripId);
    }

    private void send(String openid, Map<String, Object> data, String page) {
        String token = accessTokenService.getAccessToken();
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token={token}";

        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("page", page);
        body.put("data", data);

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
                log.warn("订阅消息发送失败: errcode={}, errmsg={}", errcode, root.path("errmsg").asText());
            } else {
                log.info("订阅消息发送成功: openid={}", openid);
            }
        } catch (Exception e) {
            log.warn("订阅消息发送异常: {}", e.getMessage());
        }
    }
}
