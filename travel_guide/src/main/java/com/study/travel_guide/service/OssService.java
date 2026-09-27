package com.study.travel_guide.service;

import com.aliyun.oss.OSS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Slf4j
@Service
public class OssService {

    private final OSS ossClient;
    private final String bucketName;
    private final String endpoint;

    public OssService(OSS ossClient,
                      @Value("${aliyun.oss.bucket-name}") String bucketName,
                      @Value("${aliyun.oss.endpoint}") String endpoint) {
        this.ossClient = ossClient;
        this.bucketName = bucketName;
        this.endpoint = endpoint;
    }

    public String upload(byte[] bytes, String objectName) {
        ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
        String host = endpoint.replace("https://", "").replace("http://", "");
        return "https://" + bucketName + "." + host + "/" + objectName;
    }

    /**
     * 上传 base64 图片（可带 data URL 前缀），按用户分目录，返回公网 URL。
     */
    public String uploadBase64Image(Long userId, String base64) {
        String data = base64;
        String ext = "jpg";
        if (data.contains(",")) {
            String prefix = data.substring(0, data.indexOf(","));
            if (prefix.contains("png")) {
                ext = "png";
            } else if (prefix.contains("gif")) {
                ext = "gif";
            } else if (prefix.contains("webp")) {
                ext = "webp";
            }
            data = data.substring(data.indexOf(",") + 1);
        }
        byte[] bytes = Base64.getDecoder().decode(data);
        String objectName = "image/" + userId + "_" + System.currentTimeMillis() + "." + ext;
        String url = upload(bytes, objectName);
        log.info("[oss] 上传图片: userId={}, {}B, {}", userId, bytes.length, url);
        return url;
    }
}
