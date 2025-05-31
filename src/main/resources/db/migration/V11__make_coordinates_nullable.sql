-- V11: Сделать координаты latitude/longitude необязательными
-- Дата: 2025-05-31
-- Описание: Убирает NOT NULL ограничения с полей latitude/longitude в таблице delivery_locations
--          для поддержки автосоздания пунктов доставки из Android приложения

-- Удаляем NOT NULL ограничения с latitude и longitude
ALTER TABLE delivery_locations
ALTER COLUMN latitude
DROP NOT NULL,
ALTER COLUMN longitude
DROP NOT NULL;

-- Устанавливаем координаты Москвы по умолчанию для существующих записей где они NULL
UPDATE delivery_locations
SET
    latitude = 55.7558,
    longitude = 37.6173
WHERE
    latitude IS NULL
    OR longitude IS NULL;

-- Добавляем комментарии к полям
COMMENT ON COLUMN delivery_locations.latitude IS 'Широта (необязательно, по умолчанию Москва)';

COMMENT ON COLUMN delivery_locations.longitude IS 'Долгота (необязательно, по умолчанию Москва)';