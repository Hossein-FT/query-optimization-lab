package com.example.querylab.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderRow(
        long id,
        long customerId,
        String status,
        LocalDateTime createdAt,
        BigDecimal totalAmount
) {
}
