-- Добавление поля total_amount в таблицу carts
ALTER TABLE carts
ADD COLUMN IF NOT EXISTS total_amount DECIMAL(10, 2) DEFAULT 0.00 NOT NULL;

COMMENT ON COLUMN carts.total_amount IS 'Общая сумма товаров в корзине';

-- Добавление колонки для скидочной цены
ALTER TABLE products ADD COLUMN discounted_price DECIMAL(10, 2);

-- -- Обновить поле active в таблице delivery_locations и order_statuses
-- UPDATE delivery_locations SET active = true WHERE active IS NULL;
-- UPDATE order_statuses SET active = true WHERE active IS NULL;
-- UPDATE products SET available = true WHERE available IS NULL;