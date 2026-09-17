package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.AsrRequest;
import com.study.travel_guide.dto.TtsRequest;
import com.study.travel_guide.service.voice.BaiduVoiceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final BaiduVoiceService baiduVoiceService;

    public VoiceController(BaiduVoiceService baiduVoiceService) {
        this.baiduVoiceService = baiduVoiceService;
    }

    @PostMapping("/asr")
    public Result<Map<String, Object>> asr(@RequestBody AsrRequest request) {
        String text = baiduVoiceService.asr(request.getAudio(), request.getFormat());
        return Result.ok(Map.of("text", text));
    }

    @PostMapping("/tts")
    public Result<Map<String, Object>> tts(@RequestBody TtsRequest request) {
        String audio = baiduVoiceService.tts(request.getText());
        return Result.ok(Map.of("audio", audio, "contentType", "audio/mp3"));
    }
}
