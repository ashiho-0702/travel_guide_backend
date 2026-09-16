package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.dto.TripSummary;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.TripMapper;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
