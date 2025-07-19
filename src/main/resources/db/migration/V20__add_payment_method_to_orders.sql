-- Добавляем поле payment_method в таблицу orders
-- Миграция V20: добавление способа оплаты к заказам

-- Добавляем новую колонку payment_method
ALTER TABLE orders 
ADD COLUMN payment_method VARCHAR(50) DEFAULT 'CASH';

-- Добавляем комментарий к колонке
COMMENT ON COLUMN orders.payment_method IS 'Способ оплаты заказа (SBP, BANK_CARD, CASH и т.д.)';

-- Обновляем существующие заказы - устанавливаем CASH по умолчанию
UPDATE orders SET payment_method = 'CASH' WHERE payment_method IS NULL;

-- Устанавливаем NOT NULL после обновления данных
ALTER TABLE orders ALTER COLUMN payment_method SET NOT NULL;

-- Добавляем check constraint для валидации значений
ALTER TABLE orders 
ADD CONSTRAINT check_payment_method 
CHECK (payment_method IN ('SBP', 'BANK_CARD', 'YOOMONEY', 'QIWI', 'WEBMONEY', 'ALFABANK', 'SBERBANK', 'CASH'));

-- Создаем индекс для быстрого поиска по способу оплаты
CREATE INDEX idx_orders_payment_method ON orders(payment_method); 