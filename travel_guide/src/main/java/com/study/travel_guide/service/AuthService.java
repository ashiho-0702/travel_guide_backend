package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.common.JwtUtil;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.growth.InviteService;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final InviteService inviteService;

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String secret;

    public AuthService(RestClient restClient, JsonMapper jsonMapper, UserMapper userMapper, JwtUtil jwtUtil,
                       InviteService inviteService) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.inviteService = inviteService;
    }

    public Map<String, Object> login(String code, Long inviterId) {
        log.info("登录请求: inviterId={}", inviterId);
        String url = "https://api.weixin.qq.com/sns/jscode2session" +
                "?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

        String json;
        try {
            json = restClient.get()
                    .uri(url, appid, secret, code)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new BizException(502, "微信接口调用失败(" + e.getStatusCode().value() + ")");
        }

        String openid;
        try {
            JsonNode root = jsonMapper.readTree(json);
            openid = root.path("openid").asText();
            if (openid == null || openid.isBlank()) {
                throw new BizException(401, "微信登录失败: " + root.path("errmsg").asText());
            }
            log.info("微信 code2session 成功: openid={}", openid);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "微信响应解析失败: " + e.getMessage());
        }

        User user = userMapper.findByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            userMapper.insert(user);
            log.info("新用户注册: userId={}, openid={}", user.getId(), openid);
            if (inviterId != null) {
                inviteService.bindInvite(inviterId, user.getId());
                log.info("绑定邀请: inviterId={}, inviteeId={}", inviterId, user.getId());
            }
            // 回读一次，让 points/growth/level/is_admin 等 DB 默认值（而非 null）写进响应
            user = userMapper.findById(user.getId());
        }

        String token = jwtUtil.generate(user.getId(), openid);
        log.info("登录成功: userId={}", user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return data;
    }
}
