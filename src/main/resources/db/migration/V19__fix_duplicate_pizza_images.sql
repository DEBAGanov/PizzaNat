-- V27__fix_duplicate_pizza_images.sql
-- Исправление проблемы с дублирующимися изображениями пицц
-- Проблема: "Пицца 4 сыра" и "Сырная пицца" используют одно изображение pizza_4_chees.png

-- Обновляем "Сырную пиццу" чтобы использовать отдельное изображение
UPDATE products
SET
    image_url = 'products/pizza_cheese.png'
WHERE
    name = 'Сырная пицца'
    AND image_url = 'products/pizza_4_chees.png';

-- Проверяем результат (для логов)
SELECT
    name,
    image_url,
    CASE
        WHEN name = 'Пицца 4 сыра'
        AND image_url = 'products/pizza_4_chees.png' THEN '✅ Правильно'
        WHEN name = 'Сырная пицца'
        AND image_url = 'products/pizza_cheese.png' THEN '✅ Исправлено'
        ELSE '❌ Проблема'
    END as status
FROM products
WHERE
    name IN (
        'Пицца 4 сыра',
        'Сырная пицца'
    )
ORDER BY name;