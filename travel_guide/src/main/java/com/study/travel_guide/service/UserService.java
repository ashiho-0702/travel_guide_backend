package com.study.travel_guide.service;

import com.study.travel_guide.mapper.UserMapper;
import com.study.travel_guide.service.wechat.ContentSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final OssService ossService;
    private final ContentSecurityService contentSecurityService;

    public UserService(UserMapper userMapper, OssService ossService,
                       ContentSecurityService contentSecurityService) {
        this.userMapper = userMapper;
        this.ossService = ossService;
        this.contentSecurityService = contentSecurityService;
    }

    public Map<String, String> updateProfile(Long userId, String nickname, String avatarBase64) {
        String avatarUrl = null;
        if (avatarBase64 != null && !avatarBase64.isBlank()) {
            avatarUrl = saveAvatar(userId, avatarBase64);
        }

        if (nickname != null && !nickname.isBlank()) {
            contentSecurityService.checkText(userId, nickname, ContentSecurityService.SCENE_PROFILE);
            userMapper.updateNickname(userId, nickname);
        }
        if (avatarUrl != null) {
            userMapper.updateAvatar(userId, avatarUrl);
        }

        Map<String, String> data = new HashMap<>();
        data.put("nickname", nickname);
        data.put("avatarUrl", avatarUrl);
        return data;
    }

    private String saveAvatar(Long userId, String avatarBase64) {
        try {
            String base64 = avatarBase64;
            // 去掉可能的 data URL 前缀（data:image/...;base64,）
            if (base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(",") + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(base64);
            String objectName = "avatar/" + userId + "_" + System.currentTimeMillis() + ".jpg";
            return ossService.upload(bytes, objectName);
        } catch (Exception e) {
            log.warn("保存头像失败: {}", e.getMessage());
            return null;
        }
    }
}
