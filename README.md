# Query Optimization Lab — Spring Boot + PostgreSQL

این پروژه یک آزمایش عملی برای بررسی Query کند، تحلیل Execution Plan و ارزیابی اثر Index روی جدول یک‌میلیون‌ردیفی `orders` است.

## ابزارها و نسخه‌ها

- Java 21
- Spring Boot 3.5.16
- PostgreSQL 17
- Spring JDBC و `JdbcTemplate`
- Docker Compose

برای اینکه نتیجه قابل مقایسه باشد، ابتدا جدول فقط Primary Key روی `id` دارد. پس از ثبت نتیجه‌ی baseline، یک Composite/Covering Index می‌سازیم و دقیقاً همان Query را دوباره اجرا می‌کنیم.

## 1. اجرا

نیاز است Docker Engine و Docker Compose نصب باشند. در پوشه‌ی پروژه اجرا کنید:

```bash
docker compose up --build -d
```

در اولین اجرا، PostgreSQL اسکریپت `db/init/01_seed_orders.sql` را اجرا می‌کند و یک میلیون ردیف می‌سازد. ایجاد داده ممکن است بسته به CPU و Disk چند دقیقه طول بکشد. وضعیت را بررسی کنید:

```bash
docker compose logs -f db
```

وقتی سرویس آماده شد، تعداد ردیف‌ها را چک کنید:

```bash
docker compose exec db psql -U app -d querylab -c "SELECT COUNT(*) FROM orders;"
```

خروجی مورد انتظار `1000000` است. سرویس Spring Boot روی پورت `8080` و PostgreSQL روی پورت `5432` در دسترس است.

> اسکریپت seed مربوط به اولین ساخت Volume است. اگر Volume قبلاً ساخته شده باشد، `docker compose up` داده‌ها را دوباره seed نمی‌کند.

## 2. Query مورد آزمایش

Query یک مشتری و وضعیت سفارش را فیلتر می‌کند، جدیدترین سفارش‌ها را اول می‌آورد و حداکثر ۲۰ ردیف برمی‌گرداند:

```sql
SELECT id, customer_id, status, created_at, total_amount
FROM orders
WHERE customer_id = 1001
  AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;
```

این Query در اپلیکیشن از طریق `JdbcTemplate` و پارامترهای bind‌شده اجرا می‌شود؛ مقادیر ورودی به SQL چسبانده نمی‌شوند.

## 3. مرحله اول — اندازه‌گیری قبل از Index

اسکریپت baseline را اجرا کنید:

```bash
docker compose exec -T db psql -U app -d querylab -v ON_ERROR_STOP=1 -f /scripts/01_before_index.sql
```

در Execution Plan دنبال این موارد بگردید:

- `Seq Scan on public.orders`: دیتابیس ردیف‌های زیادی از جدول را برای پیدا کردن سفارش‌های مطابق بررسی می‌کند.
- `Rows Removed by Filter`: تعداد ردیف‌هایی که پس از بررسی شرط‌ها کنار گذاشته شده‌اند.
- `Buffers: shared hit=... read=...`: اطلاعاتی درباره‌ی دسترسی به صفحات حافظه/دیسک.
- `Planning Time` و `Execution Time`: زمان برنامه‌ریزی و اجرای Query که PostgreSQL گزارش می‌کند.

چون فقط Primary Key روی `id` وجود دارد، برای شرط این Query فعلاً Index مناسبی نداریم. پلنر معمولاً `Seq Scan` انتخاب می‌کند؛ انتخاب دقیق همیشه به آمار، تنظیمات و محیط اجرا بستگی دارد.

## 4. مرحله دوم — ساخت Index

اسکریپت زیر Composite Index ایجاد می‌کند:

```bash
docker compose exec -T db psql -U app -d querylab -v ON_ERROR_STOP=1 -f /scripts/02_create_index.sql
```

Index ساخته‌شده:

```sql
CREATE INDEX idx_orders_customer_status_created_at
    ON orders (customer_id, status, created_at DESC)
    INCLUDE (id, total_amount);
```

دلیل طراحی:

1. `customer_id` و `status` برای فیلترها هستند.
2. `created_at DESC` ترتیب موردنیاز Query را پشتیبانی می‌کند.
3. `id` و `total_amount` با `INCLUDE` به پوشش ستون‌های خروجی کمک می‌کنند.
4. `ANALYZE` آمار جدول را به‌روز می‌کند.
5. `VACUUM (ANALYZE)` می‌تواند اطلاعات visibility map را به‌روزرسانی کند و در شرایط مناسب امکان `Index Only Scan` را فراهم کند.

`INCLUDE` ستون‌ها را به کلید مرتب‌شده‌ی Index تبدیل نمی‌کند؛ آن‌ها برای پوشش خروجی ذخیره می‌شوند.

## 5. مرحله سوم — اندازه‌گیری بعد از Index

همان Query را بدون تغییر اجرا کنید:

```bash
docker compose exec -T db psql -U app -d querylab -v ON_ERROR_STOP=1 -f /scripts/03_after_index.sql
```

پلنر ممکن است از `Index Only Scan` یا `Index Scan` استفاده کند. اگر شرایط مناسب باشد، `Index Only Scan` می‌تواند مراجعه به ردیف‌های اصلی جدول را کاهش دهد. در خروجی آن به `Heap Fetches` نیز توجه کنید.

**عدد سرعت را از قبل نمی‌توان تضمین کرد.** زمان واقعی به CPU، حافظه، Storage، Cache، بار سیستم و نسخه/تنظیمات PostgreSQL وابسته است. برای مقایسه‌ی منصفانه، هر دو مرحله را چند بار اجرا کنید و مقدار `Execution Time`، تعداد Bufferها و نوع Scan را بررسی کنید.

## 6. اجرای Query از Spring Boot

### دریافت سفارش‌ها

```bash
curl "http://localhost:8080/api/orders?customerId=1001&status=PAID&limit=20"
```

### دریافت Execution Plan واقعی

```bash
curl "http://localhost:8080/api/orders/explain?customerId=1001&status=PAID&limit=20"
```

خروجی endpoint دوم JSON است و خطوط `EXPLAIN ANALYZE` را در آرایه‌ی `plan` برمی‌گرداند. آن را یک بار قبل از ساخت Index و یک بار بعد از ساخت Index فراخوانی کنید.

Endpointهای کمکی:

- `GET /api/orders/count` — تعداد سفارش‌ها
- `GET /api/orders/health` — بررسی ساده‌ی در دسترس بودن اپلیکیشن

محدوده‌ی `limit` در API از ۱ تا ۱۰۰ است.

## 7. بررسی Indexها و اندازه‌ی آن‌ها

```bash
docker compose exec db psql -U app -d querylab -c "\d orders"

docker compose exec db psql -U app -d querylab -c "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'orders';"

docker compose exec db psql -U app -d querylab -c "SELECT pg_size_pretty(pg_relation_size('orders')) AS table_size, pg_size_pretty(pg_indexes_size('orders')) AS indexes_size;"
```

## 8. پاک‌سازی محیط آزمایش

این دستور Volume دیتابیس و داده‌ی یک‌میلیون‌ردیفی را حذف می‌کند:

```bash
docker compose down -v
```

فقط وقتی اجرا کنید که حذف کامل داده‌های آزمایش مدنظر باشد.

## ساختار پروژه

```text
query-optimization-lab/
├── db/
│   ├── init/01_seed_orders.sql
│   └── scripts/
│       ├── 01_before_index.sql
│       ├── 02_create_index.sql
│       └── 03_after_index.sql
├── src/main/java/com/example/querylab/
│   ├── QueryOptimizationLabApplication.java
│   └── orders/
│       ├── OrderController.java
│       ├── OrderQueryService.java
│       ├── OrderRow.java
│       └── ExplainResponse.java
├── src/main/resources/application.yml
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## نکته‌های فنی و مصاحبه‌ای

- Index زیاد همیشه بهتر نیست: Indexهای اضافی فضای ذخیره‌سازی مصرف می‌کنند و هزینه‌ی `INSERT`، `UPDATE` و `DELETE` را افزایش می‌دهند.
- ترتیب ستون‌ها در Composite Index مهم است؛ این نمونه برای شرط‌های `customer_id` و `status` ساخته شده است.
- `EXPLAIN ANALYZE` واقعاً Query را اجرا می‌کند؛ روی Queryهای تغییردهنده‌ی داده مانند `UPDATE` یا `DELETE` باید با احتیاط استفاده شود.
- خروجی برنامه‌ریزی‌شده با زمان درخواست HTTP یکسان نیست؛ زمان HTTP شامل بخش‌های دیگری مانند شبکه، serialization و پردازش درخواست است.
- در محیط واقعی، قبل از افزودن Index باید Queryهای پرتکرار، Selectivity، هزینه‌ی Write و اندازه‌ی Index سنجیده شود.
