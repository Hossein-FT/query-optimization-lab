package com.example.querylab.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderQueryService orderQueryService;

    public OrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping
    public List<OrderRow> findOrders(
            @RequestParam(defaultValue = "1001") long customerId,
            @RequestParam(defaultValue = "PAID") String status,
            @RequestParam(defaultValue = "20") int limit) {
        validate(customerId, status, limit);
        return orderQueryService.findOrders(customerId, status.trim().toUpperCase(Locale.ROOT), limit);
    }

    @GetMapping("/explain")
    public ExplainResponse explain(
            @RequestParam(defaultValue = "1001") long customerId,
            @RequestParam(defaultValue = "PAID") String status,
            @RequestParam(defaultValue = "20") int limit) {
        validate(customerId, status, limit);
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        List<String> plan = orderQueryService.explain(customerId, normalizedStatus, limit);
        return new ExplainResponse(
                "SELECT id, customer_id, status, created_at, total_amount FROM orders "
                        + "WHERE customer_id = ? AND status = ? "
                        + "ORDER BY created_at DESC LIMIT ?",
                customerId, normalizedStatus, limit, plan);
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("orders", orderQueryService.countOrders());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    private static void validate(long customerId, String status, int limit) {
        if (customerId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "customerId must be greater than 0");
        }
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must not be blank");
        }
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100");
        }
    }
}
