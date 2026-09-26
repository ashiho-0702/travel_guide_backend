package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.service.wechat.ContentSecurityService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripReplanServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String OLD = "{\"city\":\"杭州\",\"days\":[{\"day\":1,\"spots\":[{\"name\":\"西湖\",\"lat\":30.25,\"lng\":120.15}]}]}";
    private static final String NEW = "{\"city\":\"杭州\",\"days\":[{\"day\":1,\"spots\":[{\"name\":\"西湖\",\"reason\":\"改到下午\"}]}]}";

    @Test
    void replanRejectsBlankInstruction() {
        TripReplanService svc = new TripReplanService(null, null, null, null, null);

        BizException e = assertThrows(BizException.class, () -> svc.replan(1L, 1L, "  "));
        assertEquals(400, e.getCode());
    }

    @Test
    void replanPreservesCoordinatesForMatchingSpots() throws Exception {
        TripService tripService = mock(TripService.class);
        DeepSeekService deepSeekService = mock(DeepSeekService.class);
        TencentMapService tencentMapService = mock(TencentMapService.class);
        ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);

        Trip trip = new Trip();
        trip.setCity("杭州");
        trip.setResult(OLD);
        when(tripService.detail(1L, 10L)).thenReturn(trip);

        JsonNode newResult = JSON.readTree(NEW);
        when(deepSeekService.generateJson(anyString(), anyString())).thenReturn(newResult);
        when(tencentMapService.searchLocation(anyString(), anyString())).thenReturn(null);

        TripReplanService svc = new TripReplanService(
                tripService, deepSeekService, tencentMapService, JSON, contentSecurityService);

        JsonNode result = svc.replan(1L, 10L, "下午下雨改成室内");

        JsonNode spot = result.path("days").get(0).path("spots").get(0);
        assertEquals(30.25, spot.path("lat").asDouble(), 0.001);
        assertEquals(120.15, spot.path("lng").asDouble(), 0.001);
        verify(tripService, never()).updateResult(any(), any(), any());
    }
}
