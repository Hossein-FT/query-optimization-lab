-- Baseline: the only index should be the primary-key index on id.
-- Run this before applying 02_create_index.sql.
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)
SELECT id, customer_id, status, created_at, total_amount
FROM orders
WHERE customer_id = 1001
  AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;
