-- Same query as 01_before_index.sql; compare plan, actual time and buffer counts.
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)
SELECT id, customer_id, status, created_at, total_amount
FROM orders
WHERE customer_id = 1001
  AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;
