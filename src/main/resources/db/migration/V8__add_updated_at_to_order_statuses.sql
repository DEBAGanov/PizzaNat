-- Добавление колонки updated_at в таблицу order_statuses
ALTER TABLE order_statuses
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Обновление существующих записей
UPDATE order_statuses
SET
    updated_at = created_at
WHERE
    updated_at IS NULL;