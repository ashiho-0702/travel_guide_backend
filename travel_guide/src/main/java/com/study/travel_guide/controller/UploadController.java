package com.study.travel_guide.controller;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.common.Result;
import com.study.travel_guide.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final OssService ossService;

    public UploadController(OssService ossService) {
        this.ossService = ossService;
    }

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestAttribute("userId") Long userId,
                                                   @RequestBody Map<String, String> body) {
        String image = body.get("image");
        if (image == null || image.isBlank()) {
            throw new BizException(400, "图片不能为空");
        }
        try {
            String url = ossService.uploadBase64Image(userId, image);
            log.info("[upload] 图片上传成功: userId={}, url={}", userId, url);
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            log.warn("[upload] 图片上传失败: userId={}, {}", userId, e.getMessage());
            throw new BizException(500, "图片上传失败");
        }
    }
}
