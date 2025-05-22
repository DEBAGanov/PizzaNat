CREATE TABLE IF NOT EXISTS order_statuses (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание базовых статусов заказа (только если их еще нет)
INSERT INTO order_statuses (name, description)
SELECT 'CREATED', 'Заказ создан'
WHERE NOT EXISTS (SELECT 1 FROM order_statuses WHERE name = 'CREATED');

INSERT INTO order_statuses (name, description)
SELECT 'PROCESSING', 'Заказ обрабатывается'
WHERE NOT EXISTS (SELECT 1 FROM order_statuses WHERE name = 'PROCESSING');

INSERT INTO order_statuses (name, description)
SELECT 'READY', 'Заказ готов к выдаче'
WHERE NOT EXISTS (SELECT 1 FROM order_statuses WHERE name = 'READY');

INSERT INTO order_statuses (name, description)
SELECT 'DELIVERED', 'Заказ выдан'
WHERE NOT EXISTS (SELECT 1 FROM order_statuses WHERE name = 'DELIVERED');

INSERT INTO order_statuses (name, description)
SELECT 'CANCELED', 'Заказ отменен'
WHERE NOT EXISTS (SELECT 1 FROM order_statuses WHERE name = 'CANCELED');

-- Безопасное обновление внешнего ключа в таблице orders, если колонка status_id существует
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders' AND column_name = 'status_id'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name = 'fk_orders_status'
        ) THEN
            ALTER TABLE orders
            ADD CONSTRAINT fk_orders_status
            FOREIGN KEY (status_id)
            REFERENCES order_statuses(id);
        END IF;
    END IF;
END
$$;