-- V10: Добавление поля delivery_address для поддержки Android приложения
-- Дата: 2025-05-31
-- Описание: Добавляет колонку delivery_address в таблицу orders для поддержки
--          произвольных адресов доставки из Android приложения

-- Добавляем колонку delivery_address
ALTER TABLE orders ADD COLUMN delivery_address VARCHAR(500);

-- Добавляем комментарий к колонке
COMMENT ON COLUMN orders.delivery_address IS 'Адрес доставки (используется Android приложением как альтернатива delivery_location_id)';

-- Добавляем индекс для быстрого поиска по адресу доставки
CREATE INDEX IF NOT EXISTS idx_orders_delivery_address ON orders (delivery_address)
WHERE
    delivery_address IS NOT NULL;