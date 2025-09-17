-- V23__update_categories_and_products.sql
-- Обновление категорий и товаров: замена категории Комбо на Шаурма и исправление названий товаров

-- БЕЗОПАСНО: Отключаем товары из категории "Комбо" вместо удаления (чтобы сохранить ссылки в заказах)
UPDATE products SET is_available = false WHERE category_id = (SELECT id FROM categories WHERE name = 'Комбо');

-- БЕЗОПАСНО: Переименовываем категорию "Комбо" в "Шаурма (старая)" для ясности
UPDATE categories SET 
    name = 'Комбо (устарело)',
    description = 'Устаревшая категория товаров',
    is_active = false
WHERE name = 'Комбо';

-- Создаем новую категорию "Шаурма"
INSERT INTO categories (name, description, image_url, display_order, is_active)
VALUES (
    'Шаурма',
    'Вкусная и сытная шаурма',
    'categories/shaurma.png',
    4,
    true
) ON CONFLICT (name) DO NOTHING;

-- Добавляем товары шаурмы
DO $$
DECLARE
    shaurma_category_id INTEGER;
BEGIN
    SELECT id INTO shaurma_category_id FROM categories WHERE name = 'Шаурма';

    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Шаурма «Классическая»', 'Курица, капуста, огурцы, помидоры, соус. Вес 400 гр', 210.00, 400, shaurma_category_id, 'products/shaurma.png', true, false, null),
        ('Шаурма «По-корейски»', 'Курица, капуста, огурцы, помидоры, морковь по-корейски, соус. Вес 400 гр', 220.00, 400, shaurma_category_id, 'products/shaurma.png', true, false, null),
        ('Шаурма «Острая»', 'Курица, капуста, помидоры, огурцы, халапеньо, острый соус. Вес 400 гр', 230.00, 400, shaurma_category_id, 'products/shaurma.png', true, false, null),
        ('Шаурма c фри', 'Курица, капуста, огурцы, помидоры, картошка фри, соус. Вес 400 гр', 240.00, 400, shaurma_category_id, 'products/shaurma.png', true, false, null)
    ON CONFLICT (name) DO NOTHING;
END
$$;

-- Исправляем "Нагецы" на "Наггетсы" в названиях продуктов
UPDATE products SET name = 'Наггетсы 6 штук', description = 'Наггетсы 6 штук' WHERE name = 'Нагецы 6 штук';
UPDATE products SET name = 'Наггетсы 9 штук', description = 'Наггетсы 9 штук' WHERE name = 'Нагецы 9 штук';
UPDATE products SET name = 'Наггетсы 12 штук', description = 'Наггетсы 12 штук' WHERE name = 'Нагецы 12 штук';

-- Исправляем названия коктейлей на милкшейки с объемом
UPDATE products SET name = 'Милкшейк вишневый 500мл' WHERE name = 'Молочный коктейль';
UPDATE products SET name = 'Милкшейк шоколадный 500мл' WHERE name = 'Шоколадный коктейль';
UPDATE products SET name = 'Милкшейк клубничный 500мл' WHERE name = 'Клубничный коктейль';

-- Добавляем объем к лимонадам
UPDATE products SET name = 'Лимонад "Мохито" 500мл' WHERE name = 'Лимонад "Мохито"';
UPDATE products SET name = 'Лимонад "Тропический манго" 500мл' WHERE name = 'Лимонад "Тропический манго"';
UPDATE products SET name = 'Лимонад "Сочная малина" 500мл' WHERE name = 'Лимонад "Сочная малина"';

-- Исправляем цену мясной пиццы с 540 на 530 руб
UPDATE products SET price = 530.00 WHERE name = 'Мясная пицца';

-- Переименовываем комбо в разделе бургеры
UPDATE products SET name = 'Фри, наггетсы, напиток' WHERE name = 'Комбо 1';
UPDATE products SET name = 'Фри, чикенбургер, напиток' WHERE name = 'Комбо 2';

-- Исправляем "Нагецы" на "наггетсы" в описаниях комбо
UPDATE products SET description = 'Фри 100 гр. + Наггетсы 5 штук + соус + напиток 300 мл. ' WHERE name = 'Фри, наггетсы, напиток';
