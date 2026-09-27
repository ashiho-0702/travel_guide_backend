package com.study.travel_guide.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRequest {
    private String category;
    private BigDecimal amount;
    private String note;
    private LocalDate expenseDate;
}
