package com.study.travel_guide.service.guide;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.service.TencentMapService;
import com.study.travel_guide.service.qwen.QwenVlService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AttractionIdentifyService {

    private final TencentMapService tencentMapService;
    private final QwenVlService qwenVlService;

    public AttractionIdentifyService(TencentMapService tencentMapService, QwenVlService qwenVlService) {
        this.tencentMapService = tencentMapService;
        this.qwenVlService = qwenVlService;
    }

    public Map<String, Object> identify(Double lat, Double lng, String image) {
        // 优先定位
        if (lat != null && lng != null) {
            String name = tencentMapService.searchNearby(lat, lng);
            if (name != null && !name.isBlank()) {
                Map<String, Object> result = new HashMap<>();
                result.put("attraction", name);
                result.put("source", "location");
                return result;
            }
        }
        // 拍照兜底
        if (image != null && !image.isBlank()) {
            String name = qwenVlService.identifyAttraction(image);
            if (name != null && !name.isBlank()) {
                Map<String, Object> result = new HashMap<>();
                result.put("attraction", name);
                result.put("source", "image");
                return result;
            }
        }
        throw new BizException("未能识别景点，请尝试拍照");
    }
}
