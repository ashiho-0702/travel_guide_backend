package com.study.travel_guide;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class TtsDecoder {

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get("travel_guide", "src", "test", "java", "com", "study", "travel_guide");

        Path input = dir.resolve("tts_base64.txt");
        if (!Files.exists(input)) {
            System.out.println("请先把 /api/voice/tts 返回的内容保存到：" + input.toAbsolutePath());
            return;
        }

        String content = Files.readString(input, StandardCharsets.UTF_8).trim();

        // 如果贴的是完整 JSON 响应（含 audio 字段），自动提取
        String base64 = content;
        int idx = content.indexOf("\"audio\"");
        if (idx >= 0) {
            int start = content.indexOf(':', idx) + 1;
            while (start < content.length() && (content.charAt(start) == ' ' || content.charAt(start) == '"')) {
                start++;
            }
            int end = content.indexOf('"', start);
            if (end > start) {
                base64 = content.substring(start, end);
            }
        }
        // 去掉所有非 base64 字符（空白、BOM 等）
        base64 = base64.replaceAll("[^A-Za-z0-9+/=]", "");

        System.out.println("base64 前 20 字符：" + base64.substring(0, Math.min(20, base64.length())));

        byte[] bytes = Base64.getDecoder().decode(base64);
        Path output = dir.resolve("tts_output.mp3");
        Files.write(output, bytes);

        boolean isMp3 = (bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3')
                || (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0);

        System.out.println("已输出: " + output.toAbsolutePath() + " (" + bytes.length + " 字节)");
        if (!isMp3) {
            System.out.println("⚠️ 内容不像 MP3。前 4 字节："
                    + String.format("%02X %02X %02X %02X",
                    bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF));
        } else {
            System.out.println("✓ 看起来是有效的 MP3");
        }
    }
}
