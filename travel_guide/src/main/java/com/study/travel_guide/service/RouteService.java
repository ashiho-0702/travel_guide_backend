package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.dto.RoutePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图多点路线规划：给定多个标注点（+ 可选起点），按距离最近邻排序，返回顺序 + 腾讯步行距离/时长。
 */
@Slf4j
@Service
public class RouteService {

    private final TencentMapService tencentMapService;

    public RouteService(TencentMapService tencentMapService) {
        this.tencentMapService = tencentMapService;
    }

    public Map<String, Object> optimize(RoutePoint origin, List<RoutePoint> points) {
        if (points == null || points.isEmpty()) {
            throw new BizException(400, "请至少标注一个景点");
        }
        for (RoutePoint p : points) {
            if (p.getLat() == null || p.getLng() == null) {
                throw new BizException(400, "景点缺坐标：" + (p.getName() == null ? "" : p.getName()));
            }
        }

        // 最近邻排序
        List<RoutePoint> ordered = new ArrayList<>();
        boolean hasOrigin = origin != null && origin.getLat() != null && origin.getLng() != null;
        log.info("[route] 优化路线: {} 个点, 有起点={}, origin=({},{}), 第一个景点=({},{})",
                points.size(), hasOrigin,
                origin == null ? null : origin.getLat(), origin == null ? null : origin.getLng(),
                points.get(0).getLat(), points.get(0).getLng());
        double curLat = hasOrigin ? origin.getLat() : points.get(0).getLat();
        double curLng = hasOrigin ? origin.getLng() : points.get(0).getLng();
        boolean[] visited = new boolean[points.size()];
        for (int i = 0; i < points.size(); i++) {
            int nearest = -1;
            double minDist = Double.MAX_VALUE;
            for (int j = 0; j < points.size(); j++) {
                if (visited[j]) continue;
                double d = sqDist(curLat, curLng, points.get(j));
                if (d < minDist) {
                    minDist = d;
                    nearest = j;
                }
            }
            RoutePoint next = points.get(nearest);
            ordered.add(next);
            visited[nearest] = true;
            curLat = next.getLat();
            curLng = next.getLng();
        }

        // 逐段路线距离（起点可选，腾讯 QPS=1 节流）
        List<Map<String, Object>> route = new ArrayList<>();
        int totalDistance = 0, totalDuration = 0;
        long lastRequestAt = 0;
        double fromLat = hasOrigin ? origin.getLat() : ordered.get(0).getLat();
        double fromLng = hasOrigin ? origin.getLng() : ordered.get(0).getLng();
        for (int i = 0; i < ordered.size(); i++) {
            RoutePoint p = ordered.get(i);
            Map<String, Object> step = new HashMap<>();
            step.put("name", p.getName());
            step.put("lat", p.getLat());
            step.put("lng", p.getLng());
            if (!hasOrigin && i == 0) {
                step.put("distance", null);
                step.put("duration", null);
                route.add(step);
                continue;
            }
            long wait = 1200 - (System.currentTimeMillis() - lastRequestAt);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            lastRequestAt = System.currentTimeMillis();
            Map<String, Integer> info = tencentMapService.routeInfo(fromLat, fromLng, p.getLat(), p.getLng());
            step.put("distance", info == null ? null : info.get("distance"));
            step.put("duration", info == null ? null : info.get("duration"));
            if (info != null && info.get("distance") != null) {
                totalDistance += info.get("distance");
            }
            if (info != null && info.get("duration") != null) {
                totalDuration += info.get("duration");
            }
            route.add(step);
            fromLat = p.getLat();
            fromLng = p.getLng();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("route", route);
        data.put("totalDistance", totalDistance);
        data.put("totalDuration", totalDuration);
        log.info("[route] 优化完成: 总距离={}m, 总时长={}s", totalDistance, totalDuration);
        return data;
    }

    private double sqDist(double lat1, double lng1, RoutePoint p) {
        double dlat = lat1 - p.getLat();
        double dlng = (lng1 - p.getLng()) * Math.cos(Math.toRadians((lat1 + p.getLat()) / 2));
        return dlat * dlat + dlng * dlng;
    }
}
