-- Добавление полей для управления типом доставки и её стоимостью
-- V22__add_delivery_type_and_cost.sql

-- Добавляем поле для способа доставки
ALTER TABLE orders 
ADD COLUMN delivery_type VARCHAR(100);

-- Добавляем поле для стоимости доставки (отдельно от общей суммы)
ALTER TABLE orders 
ADD COLUMN delivery_cost DECIMAL(10, 2) DEFAULT 0.00;

-- Добавляем комментарии к новым полям
COMMENT ON COLUMN orders.delivery_type IS 'Способ доставки: Самовывоз или Доставка курьером';
COMMENT ON COLUMN orders.delivery_cost IS 'Стоимость доставки (отдельно от стоимости товаров)';

-- Устанавливаем значения по умолчанию для существующих заказов
UPDATE orders 
SET delivery_type = 'Самовывоз', 
    delivery_cost = 0.00 
WHERE delivery_type IS NULL;

-- Добавляем индекс для быстрого поиска по типу доставки
CREATE INDEX idx_orders_delivery_type ON orders (delivery_type);

-- Добавляем проверочное ограничение для delivery_cost (не может быть отрицательной)
ALTER TABLE orders 
ADD CONSTRAINT check_delivery_cost_non_negative 
CHECK (delivery_cost >= 0); 