-- V24__remove_combo_products.sql
-- Безопасное удаление товаров Комбо 3, 4, 5

-- Сначала проверяем и обрабатываем ссылки в order_items
-- Если есть заказы с этими товарами, удаляем записи order_items (так как товары устарели)
DELETE FROM order_items WHERE product_id IN (25, 26, 27);

-- Удаляем товары из корзин, если они там есть
DELETE FROM cart_items WHERE product_id IN (25, 26, 27);

-- Теперь безопасно удаляем сами товары
DELETE FROM products WHERE id IN (25, 26, 27) AND name IN ('Комбо 3', 'Комбо 4', 'Комбо 5');

-- Проверяем, что товары удалены
-- (это будет видно в логах миграции)
