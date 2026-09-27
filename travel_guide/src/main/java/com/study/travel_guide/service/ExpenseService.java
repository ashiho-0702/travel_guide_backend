package com.study.travel_guide.service;

import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.Expense;
import com.study.travel_guide.mapper.ExpenseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开支记录：每个行程一份，分类记账（住宿/交通/餐饮/临时支出）。
 */
@Slf4j
@Service
public class ExpenseService {

    private static final Set<String> CATEGORIES = Set.of("accommodation", "transport", "food", "misc");

    private final TripService tripService;
    private final ExpenseMapper expenseMapper;

    public ExpenseService(TripService tripService, ExpenseMapper expenseMapper) {
        this.tripService = tripService;
        this.expenseMapper = expenseMapper;
    }

    public List<Expense> list(Long userId, Long tripId) {
        tripService.detailAsMember(userId, tripId);
        return expenseMapper.listByTrip(tripId);
    }

    public List<Expense> add(Long userId, Long tripId, String category, BigDecimal amount, String note, LocalDate expenseDate) {
        tripService.detailAsMember(userId, tripId);
        validate(category, amount);
        LocalDate date = expenseDate == null ? LocalDate.now() : expenseDate;
        expenseMapper.insert(tripId, category, amount, note, date);
        log.info("[expense] 新增支出: tripId={}, category={}, amount={}", tripId, category, amount);
        return expenseMapper.listByTrip(tripId);
    }

    public void update(Long userId, Long tripId, Long expenseId, String category, BigDecimal amount, String note, LocalDate expenseDate) {
        tripService.detailAsMember(userId, tripId);
        validate(category, amount);
        LocalDate date = expenseDate == null ? LocalDate.now() : expenseDate;
        int affected = expenseMapper.update(expenseId, tripId, category, amount, note, date);
        if (affected == 0) {
            throw new BizException(404, "支出记录不存在");
        }
        log.info("[expense] 更新支出: tripId={}, expenseId={}", tripId, expenseId);
    }

    public void delete(Long userId, Long tripId, Long expenseId) {
        tripService.detailAsMember(userId, tripId);
        expenseMapper.delete(expenseId, tripId);
        log.info("[expense] 删除支出: tripId={}, expenseId={}", tripId, expenseId);
    }

    public Map<String, Object> summary(Long userId, Long tripId) {
        tripService.detailAsMember(userId, tripId);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (Map<String, Object> row : expenseMapper.sumByCategory(tripId)) {
            String category = (String) row.get("category");
            if (category == null) {
                continue;
            }
            BigDecimal amount = new BigDecimal(String.valueOf(row.get("total"))).setScale(2, RoundingMode.HALF_UP);
            byCategory.put(category, amount);
            total = total.add(amount);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", total.setScale(2, RoundingMode.HALF_UP));
        data.put("categories", byCategory);
        return data;
    }

    private void validate(String category, BigDecimal amount) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new BizException(400, "分类不合法");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "金额不能为负");
        }
    }
}
