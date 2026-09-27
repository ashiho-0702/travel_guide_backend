package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.Trip;
import com.study.travel_guide.mapper.ExpenseMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpenseServiceTest {

    private final TripService tripService = mock(TripService.class);
    private final ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
    private final ExpenseService service = new ExpenseService(tripService, expenseMapper);

    private Trip trip() {
        Trip t = new Trip();
        t.setId(10L);
        return t;
    }

    private Map<String, Object> row(String category, BigDecimal amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("category", category);
        m.put("total", amount);
        return m;
    }

    @Test
    void addRejectsInvalidCategory() {
        when(tripService.detailAsMember(1L, 10L)).thenReturn(trip());
        assertThrows(BizException.class, () -> service.add(1L, 10L, "bad", BigDecimal.TEN, null, null));
    }

    @Test
    void addRejectsNegativeAmount() {
        when(tripService.detailAsMember(1L, 10L)).thenReturn(trip());
        assertThrows(BizException.class, () -> service.add(1L, 10L, "food", new BigDecimal("-1"), null, null));
    }

    @Test
    void summaryAggregatesTotal() {
        when(tripService.detailAsMember(1L, 10L)).thenReturn(trip());
        when(expenseMapper.sumByCategory(10L)).thenReturn(List.of(
                row("food", new BigDecimal("100.00")),
                row("transport", new BigDecimal("50.50"))
        ));

        Map<String, Object> result = service.summary(1L, 10L);

        assertEquals(new BigDecimal("150.50"), result.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> cats = (Map<String, BigDecimal>) result.get("categories");
        assertEquals(new BigDecimal("100.00"), cats.get("food"));
    }
}
