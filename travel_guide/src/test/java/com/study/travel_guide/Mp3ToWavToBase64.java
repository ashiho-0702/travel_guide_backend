package com.study.travel_guide;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class Mp3ToWavToBase64 {

    public static void main(String[] args) throws Exception {
        // 和 Mp3ToWavToBase64.java 同级目录（工作目录是项目根 miniProgram/）
        Path dir = Paths.get("travel_guide", "src", "test", "java", "com", "study", "travel_guide");

        Path mp3File = dir.resolve("input.mp3");
        if (!Files.exists(mp3File)) {
            System.out.println("请先把 mp3 文件放到：" + mp3File.toAbsolutePath());
            return;
        }

        // 1. 读 mp3
        AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3File.toFile());

        // 2. 转成 16kHz 单声道 16bit（百度 ASR 要求）
        AudioFormat target = new AudioFormat(16000, 16, 1, true, false);
        AudioInputStream wavStream = AudioSystem.getAudioInputStream(target, mp3Stream);

        // 3. 写 wav 文件
        Path wavFile = dir.resolve("output.wav");
        AudioSystem.write(wavStream, AudioFileFormat.Type.WAVE, wavFile.toFile());

        // 4. wav 转 base64
        byte[] wavBytes = Files.readAllBytes(wavFile);
        String base64 = Base64.getEncoder().encodeToString(wavBytes);

        // 5. base64 写文件（方便复制）
        Path base64File = dir.resolve("wav_base64.txt");
        Files.write(base64File, base64.getBytes(StandardCharsets.UTF_8));

        System.out.println("wav 已生成：" + wavFile.toAbsolutePath() + " (" + wavBytes.length + " 字节)");
        System.out.println("base64 已生成：" + base64File.toAbsolutePath() + " (" + base64.length() + " 字符)");
    }
}
