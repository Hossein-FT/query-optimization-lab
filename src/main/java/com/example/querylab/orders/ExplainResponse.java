package com.example.querylab.orders;

import java.util.List;

public record ExplainResponse(
        String query,
        long customerId,
        String status,
        int limit,
        List<String> plan
) {
}
