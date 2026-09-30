package com.study.travel_guide.service.wechat;

import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.entity.User;
import com.study.travel_guide.mapper.TripMapper;
import com.study.travel_guide.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 旅行开始提醒：每天 9 点扫描「今天开始」的行程，给用户发订阅消息。
 */
@Slf4j
@Component
public class TripStartReminderTask {

    private final TripMapper tripMapper;
    private final UserMapper userMapper;
    private final SubscribeMessageService subscribeMessageService;

    public TripStartReminderTask(TripMapper tripMapper, UserMapper userMapper,
                                 SubscribeMessageService subscribeMessageService) {
        this.tripMapper = tripMapper;
        this.userMapper = userMapper;
        this.subscribeMessageService = subscribeMessageService;
    }

    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Shanghai")
    public void sendStartReminders() {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        List<Trip> trips = tripMapper.findByStartDate(today);
        log.info("[remind] 今日开始行程 {} 个", trips == null ? 0 : trips.size());
        if (trips == null || trips.isEmpty()) {
            return;
        }
        for (Trip trip : trips) {
            try {
                User user = userMapper.findById(trip.getUserId());
                if (user != null && user.getOpenid() != null && !user.getOpenid().isBlank()) {
                    subscribeMessageService.sendTripStartReminder(user.getOpenid(), trip.getCity(), trip.getDays(), trip.getId());
                }
            } catch (Exception e) {
                log.warn("[remind] 发送提醒失败 tripId={}: {}", trip.getId(), e.getMessage());
            }
        }
    }
}
