
-- Добавляем UNIQUE ограничения для поля name в таблицах
ALTER TABLE categories ADD CONSTRAINT categories_name_unique UNIQUE (name);
ALTER TABLE products ADD CONSTRAINT products_name_unique UNIQUE (name);
ALTER TABLE delivery_locations ADD CONSTRAINT delivery_locations_name_unique UNIQUE (name);

-- Создание категории "Пиццы"
INSERT INTO categories (name, description, image_url, display_order, is_active)
VALUES ('Пиццы', 'Вкусные и ароматные пиццы', 'categories/pizza.png', 1, true)
ON CONFLICT (name) DO NOTHING;

-- Создание категории "<Бургеры>"
INSERT INTO categories (name, description, image_url, display_order, is_active)
VALUES ('Бургеры', 'Свежие и аппетитные бургеры', 'categories/pizza.png', 2, true)
ON CONFLICT (name) DO NOTHING;

-- Определяем ID категории "Пиццы"
DO $$
DECLARE
    pizza_category_id INTEGER;
BEGIN
    SELECT id INTO pizza_category_id FROM categories WHERE name = 'Пиццы';

    -- Вставка данных о продуктах с указанием BigDecimal для цены
    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Пицца Маргарита', 'Классическая итальянская пицца с томатным соусом и сыром', 499.00, 450, pizza_category_id, 'products/pizza_margarita.png', true, false, null),
        ('Пицца Пепперони', 'Острая пицца с пепперони и сыром', 599.00, 480, pizza_category_id, 'products/pizza_peperoni.png', true, true, 15),
        ('Гавайская пицца', 'Пицца с ветчиной и ананасами', 549.00, 470, pizza_category_id, 'products/pizza_gavaiyaskay.png', true, false, null),
        ('Сырная пицца', 'Пицца с четырьмя видами сыра', 579.00, 460, pizza_category_id, 'products/pizza_chees.png', true, false, null),
        ('Пицца 5 сыров', 'Пицца с пятью сортами сыра', 649.00, 490, pizza_category_id, 'products/pizza_5_chees.png', true, true, 10),
        ('Мясная пицца', 'Пицца с ассорти из мясных ингредиентов', 649.00, 520, pizza_category_id, 'products/pizza_mzysnay.png', true, false, null),
        ('Пицца Марио', 'Фирменная пицца с беконом и грибами', 599.00, 500, pizza_category_id, 'products/pizza_mario.png', true, true, 15),
        ('Пицца Карбонара', 'Пицца в стиле пасты карбонара', 629.00, 490, pizza_category_id, 'products/pizza_karbonara.png', true, false, null),
        ('Грибная пицца', 'Пицца с шампиньонами и лесными грибами', 559.00, 470, pizza_category_id, 'products/pizza_gribnaya.png', true, false, null),
        ('Пицца Том Ям', 'Острая пицца в стиле тайского супа', 679.00, 490, pizza_category_id, 'products/pizza_tom_yam.png', true, true, 12)
    ON CONFLICT (name) DO NOTHING;

    -- Обновляем скидочные цены для акционных товаров
    UPDATE products
    SET discounted_price = ROUND(price * (1 - discount_percent / 100.0), 2)
    WHERE is_special_offer = true AND discounted_price IS NULL;
END
$$;

-- Добавление пунктов выдачи
INSERT INTO delivery_locations (name, address, latitude, longitude, is_active)
VALUES
    ('Пункт выдачи #1', 'ул. Ленина, 10', 55.7558, 37.6173, true),
    ('Пункт выдачи #2', 'ул. Пушкина, 15', 55.7539, 37.6208, true)
ON CONFLICT (name) DO NOTHING;
