package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.PackingItemMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackingServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TripService tripService = mock(TripService.class);
    private final PackingItemMapper packingItemMapper = mock(PackingItemMapper.class);
    private final DeepSeekService deepSeekService = mock(DeepSeekService.class);

    private final PackingService service = new PackingService(tripService, packingItemMapper, deepSeekService);

    private Trip trip() {
        Trip t = new Trip();
        t.setId(10L);
        t.setCity("杭州");
        t.setDays(3);
        return t;
    }

    @Test
    void addRejectsBlankName() {
        when(tripService.detailAsMember(1L, 10L)).thenReturn(trip());

        BizException e = assertThrows(BizException.class, () -> service.add(1L, 10L, "  "));
        assertEquals(400, e.getCode());
    }

    @Test
    void generateParsesItemsAndInserts() throws Exception {
        when(tripService.detailAsMember(1L, 10L)).thenReturn(trip());
        JsonNode node = JSON.readTree("{\"items\":[\"身份证\",\"充电线\"]}");
        when(deepSeekService.generateJson(anyString(), anyString())).thenReturn(node);

        service.generate(1L, 10L);

        verify(packingItemMapper).deleteByTrip(10L);
        verify(packingItemMapper).insert(10L, "身份证", false);
        verify(packingItemMapper).insert(10L, "充电线", false);
    }

    @Test
    void generateKeepsExistingWhenAiReturnsEmpty() throws Exception {
        when(tripService.detailAsMember(1L, 10L)).thenReturn(trip());
        JsonNode node = JSON.readTree("{\"items\":[]}");
        when(deepSeekService.generateJson(anyString(), anyString())).thenReturn(node);

        service.generate(1L, 10L);

        verify(packingItemMapper, never()).deleteByTrip(anyLong());
    }
}
