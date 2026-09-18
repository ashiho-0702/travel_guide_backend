package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.TripMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TripService {

    private final TripMapper tripMapper;

    public TripService(TripMapper tripMapper) {
        this.tripMapper = tripMapper;
    }

    public Trip detail(Long userId, Long id) {
        Trip trip = tripMapper.findByIdAndUser(id, userId);
        if (trip == null) {
            throw new BizException(404, "行程不存在");
        }
        return trip;
    }

    public List<TripSummary> list(Long userId) {
        return tripMapper.listSummaries(userId);
    }

    public void delete(Long userId, Long id) {
        int affected = tripMapper.deleteByIdAndUser(id, userId);
        if (affected == 0) {
            throw new BizException(404, "行程不存在");
        }
    }

    public String ensureShareToken(Long userId, Long id) {
        Trip trip = detail(userId, id);
        if (trip.getShareToken() != null && !trip.getShareToken().isBlank()) {
            return trip.getShareToken();
        }
        // 16 位 token：scene = "token=" + 16 = 22 字符，满足微信 scene 上限 32 字符
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        tripMapper.updateShareToken(id, userId, token);
        return token;
    }

    public Trip getByShareToken(String token) {
        Trip trip = tripMapper.findByShareToken(token);
        if (trip == null) {
            throw new BizException(404, "分享不存在或已失效");
        }
        return trip;
    }

    public void revokeShare(Long userId, Long id) {
        detail(userId, id);
        tripMapper.clearShareToken(id, userId);
    }
}
