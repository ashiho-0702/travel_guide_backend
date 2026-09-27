package com.study.travel_guide.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Expense {
    private Long id;
    private Long tripId;
    private String category;
    private BigDecimal amount;
    private String note;
    private LocalDate expenseDate;
    private LocalDateTime createdAt;
}
