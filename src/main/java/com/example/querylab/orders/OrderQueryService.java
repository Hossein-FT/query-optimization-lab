package com.example.querylab.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class OrderQueryService {
    private static final String FIND_ORDERS_SQL = """
            SELECT id, customer_id, status, created_at, total_amount
            FROM orders
            WHERE customer_id = ?
              AND status = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

    private static final RowMapper<OrderRow> ORDER_ROW_MAPPER = (rs, rowNum) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new OrderRow(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("status"),
                createdAt.toLocalDateTime(),
                rs.getBigDecimal("total_amount")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public OrderQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OrderRow> findOrders(long customerId, String status, int limit) {
        return jdbcTemplate.query(FIND_ORDERS_SQL, ORDER_ROW_MAPPER,
                customerId, status, limit);
    }

    /** Executes the same query and returns PostgreSQL's real EXPLAIN ANALYZE output. */
    public List<String> explain(long customerId, String status, int limit) {
        String explainSql = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS) "
                + FIND_ORDERS_SQL;
        return jdbcTemplate.queryForList(explainSql, String.class,
                customerId, status, limit);
    }

    public long countOrders() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return count == null ? 0L : count;
    }
}
