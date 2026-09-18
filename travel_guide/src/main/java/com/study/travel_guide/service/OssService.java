package com.study.travel_guide.service;

import com.aliyun.oss.OSS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

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
}
