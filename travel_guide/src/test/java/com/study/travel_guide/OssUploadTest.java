package com.study.travel_guide;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * OSS 上传连通性测试（不依赖 Spring / 微信登录）。
 * 运行：IDEA 右键 Run，Program arguments 依次填 4 个参数：
 *   <endpoint> <accessKeyId> <accessKeySecret> <bucketName>
 * 例如：https://oss-cn-beijing.aliyuncs.com YOUR_AK YOUR_SK javaweb-study-gdou
 */
public class OssUploadTest {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("用法: <endpoint> <accessKeyId> <accessKeySecret> <bucketName>");
            return;
        }
        String endpoint = args[0];
        String accessKeyId = args[1];
        String accessKeySecret = args[2];
        String bucketName = args[3];

        OSS oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            byte[] bytes = "hello oss".getBytes(StandardCharsets.UTF_8);
            String objectName = "test/connectivity.txt";
            oss.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
            String host = endpoint.replace("https://", "").replace("http://", "");
            System.out.println("上传成功: https://" + bucketName + "." + host + "/" + objectName);
        } catch (Exception e) {
            System.err.println("上传失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            oss.shutdown();
        }
    }
}
