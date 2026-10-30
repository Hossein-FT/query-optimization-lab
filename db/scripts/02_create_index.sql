-- Composite index matches the filters and requested sort order.
-- INCLUDE makes the index potentially cover the selected output columns.
CREATE INDEX IF NOT EXISTS idx_orders_customer_status_created_at
    ON orders (customer_id, status, created_at DESC)
    INCLUDE (id, total_amount);

ANALYZE orders;

-- VACUUM enables PostgreSQL to mark stable pages all-visible, which can help
-- the planner use an Index Only Scan. VACUUM must not run inside a transaction.
VACUUM (ANALYZE, VERBOSE) orders;
