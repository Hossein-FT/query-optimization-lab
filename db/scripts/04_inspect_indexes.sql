SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'orders'
ORDER BY indexname;

SELECT
    pg_size_pretty(pg_relation_size('orders')) AS table_size,
    pg_size_pretty(pg_indexes_size('orders')) AS indexes_size,
    pg_size_pretty(pg_total_relation_size('orders')) AS total_size;
