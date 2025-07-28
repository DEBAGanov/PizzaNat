-- Добавляем UNIQUE ограничения для поля name в таблицах
ALTER TABLE categories
ADD CONSTRAINT categories_name_unique UNIQUE (name);

ALTER TABLE products
ADD CONSTRAINT products_name_unique UNIQUE (name);

ALTER TABLE delivery_locations
ADD CONSTRAINT delivery_locations_name_unique UNIQUE (name);

-- Создание категории "Пиццы"
INSERT INTO
    categories (
        name,
        description,
        image_url,
        display_order,
        is_active
    )
VALUES (
        'Пиццы',
        'Вкусные и ароматные пиццы',
        'categories/pizza.png',
        1,
        true
    ) ON CONFLICT (name) DO NOTHING;

-- Создание категории "<Бургеры>"
INSERT INTO
    categories (
        name,
        description,
        image_url,
        display_order,
        is_active
    )
VALUES (
        'Бургеры',
        'Свежие и аппетитные бургеры',
        'categories/burghers.png',
        2,
        true
    ) ON CONFLICT (name) DO NOTHING;

-- Создание категории "Напитки"
INSERT INTO
    categories (
        name,
        description,
        image_url,
        display_order,
        is_active
    )
VALUES (
        'Напитки',
        'Вкусные и ароматные напитки',
        'categories/drinks.png',
        3,
        true
    ) ON CONFLICT (name) DO NOTHING;

-- Создание категории "Десерты"

INSERT INTO
    categories (
        name,
        description,
        image_url,
        display_order,
        is_active
    )
VALUES (
        'Десерты',
        'Вкусные и ароматные десерты',
        'categories/desserts.png',
        4,
        true
    ) ON CONFLICT (name) DO NOTHING;

-- Создание категории "Закуски"

INSERT INTO
    categories (
        name,
        description,
        image_url,
        display_order,
        is_active
    )
VALUES (
        'Закуски',
        'Вкусные и ароматные закуски',
        'categories/snacks.png',
        5,
        true
    ) ON CONFLICT (name) DO NOTHING;

-- Создание категории "Салаты"

INSERT INTO
    categories (
        name,
        description,
        image_url,
        display_order,
        is_active
    )
VALUES (
        'Салаты',
        'Вкусные и ароматные салаты',
        'categories/salads.png',
        6,
        true
    ) ON CONFLICT (name) DO NOTHING;

-- Определяем ID категории "Пиццы"
DO $$
DECLARE
    pizza_category_id INTEGER;
BEGIN
    SELECT id INTO pizza_category_id FROM categories WHERE name = 'Пиццы';

    -- Вставка данных о продуктах с указанием BigDecimal для цены
    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Пицца Маргарита', 'Классическая итальянская пицца с томатным соусом и сыром "Моцарелла" и помидорами. Вес 580 гр.', 380.00, 450, pizza_category_id, 'products/pizza_margarita.png', true, false, null),
        ('Грибная пицца', 'Пицца с шампиньонами и томатным соусом, с сыром "Моцарелла" и ветчиной. Вес 600 гр. ', 559.00, 470, pizza_category_id, 'products/pizza_gribnaya.png', true, false, null),
        ('Сырная пицца', 'Пицца с четырьмя видами сыра (соус томатный, "Моцарелла", "Голландский", "Пармезан", "Масдам". Вес 450 гр.) ', 1.00, 460, pizza_category_id, 'products/pizza_4_chees.png', true, false, null),
        ('Пицца Салями', 'Пицца с салями и сыром (соус томатный, сыр "Моцарелла", колбаса, помидоры. Вес 580 гр.)', 1.00, 480, pizza_category_id, 'products/pizza_peperoni.png', true, true, 15),
        ('Пицца Пепперони', 'Пицца с пепперони и сыром (соус томатный, сыр "Моцарелла", пеперони. Вес 600 гр.)', 1.00, 480, pizza_category_id, 'products/pizza_peperoni.png', true, true, 15),
        ('Пицца Цезарь', 'Фирменная пицца с соусом Цезарь (соус Цезарь, сыр, курица, помидоры, салат. Вес 690 гр.)', 1.00, 500, pizza_category_id, 'products/pizza_mario.png', true, true, 15),
        ('Гавайская пицца', 'Пицца с курицей и ананасами (соус сливочный, сыр "Моцарелла", курица, ананасы. Вес 600 гр )', 1.00, 470, pizza_category_id, 'products/pizza_gavaiyaskay.png', true, false, null),
        ('Мясная пицца', 'Пицца с ассорти из мясных ингредиентов (соус томатный, сыр "Моцарелла", курица, колбаски, салями, ветчина. Вес 650 гр.)', 1.00, 520, pizza_category_id, 'products/pizza_mzysnay.png', true, false, null),
        ('Домашняя пицца', 'Пицца домашняя (соус томатный, сыр "Моцарелла", колбсалями, ветчина, помидоры, огурец маринованный, лук. Вес 610 гр. )', 1.00, 490, pizza_category_id, 'products/pizza_5_chees.png', true, true, 10),
        ('Пицца Морская', 'Пицца Морская (соус томатный, сыр "Моцарелла", морской коктель, маслины. Вес 650 гр.  )', 1.00, 490, pizza_category_id, 'products/pizza_karbonara.png', true, false, null),
        ('Груша с горогонзолой', 'Пицца груша с Горогонзолой (соус сливочный, сыр "Моцарелла", груша, сыр Горгонзола. Вес 570 гр.)', 679.00, 490, pizza_category_id, 'products/pizza_tom_yam.png', true, true, 12),
        ('Пицца конструктор', 'Пицца из двух видов пицц на выбор. Вес 570 гр.)', 679.00, 490, pizza_category_id, 'products/pizza_tom_yam.png', true, true, 12)
    ON CONFLICT (name) DO NOTHING;

    -- Обновляем скидочные цены для акционных товаров
    UPDATE products
    SET discounted_price = ROUND(price * (1 - discount_percent / 100.0), 2)
    WHERE is_special_offer = true AND discounted_price IS NULL;
END
$$;

-- Определяем ID категории "Бургеры"
DO $$
DECLARE
    burgers_category_id INTEGER;
BEGIN
    SELECT id INTO burgers_category_id FROM categories WHERE name = 'Бургеры';

    -- Вставка данных о продуктах с указанием BigDecimal для цены
    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Бургер "Димбургер"', 'Бургер с жареным луком и сыром (куриная котлета, лук, помидоры, салат, двойной сыр, маринованные орурцы, соус)', 1.00, 450, burgers_category_id, 'products/burger_classic.png', true, false, null),
        ('Бургер "Чизбургер"', 'Бургер с сыром и салатом (куриная котлета, лук, помидоры, салат, сыр, соус)', 599.00, 480, burgers_category_id, 'products/burger_cheeseburger.png', true, true, 15),
        ('Бургер "Чикенбургер"', 'Чикенбургер (куриная котлета, лук, помидоры, салат, соус)', 549.00, 470, burgers_category_id, 'products/burger_hawaiian.png', true, false, null),
        ('Бургер "Джуниор"', 'Джуниор (куриная котлета, лук, помидоры, соус)', 679.00, 490, burgers_category_id, 'products/burger_tom_yam.png', true, true, 12),
        ('Комбо 1', 'Фри 100 гр. + Нагецы 5 штук + соус + напиток 300 мл. ', 679.00, 490, burgers_category_id, 'products/burger_tom_yam.png', true, true, 12),
        ('Комбо 2', 'Фри 100 гр. + Чикенбургер + соус + напиток 300 мл. ', 679.00, 490, burgers_category_id, 'products/burger_tom_yam.png', true, true, 12)
    ON CONFLICT (name) DO NOTHING;

    -- Обновляем скидочные цены для акционных товаров
    UPDATE products
    SET discounted_price = ROUND(price * (1 - discount_percent / 100.0), 2)
    WHERE is_special_offer = true AND discounted_price IS NULL;
END
$$;

-- Определяем ID категории "Напитки"
DO $$
DECLARE
    drinks_category_id INTEGER;
BEGIN
    SELECT id INTO drinks_category_id FROM categories WHERE name = 'Напитки';

    -- Вставка данных о продуктах с указанием BigDecimal для цены
    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Молочный коктейль', 'Молочный коктейль с мороженным.', 499.00, 450, drinks_category_id, 'products/drink_cola.png', true, false, null),
        ('Шоколадный молочный коктейль', 'Молочный коктейль с шоколадным мороженным.', 499.00, 450, drinks_category_id, 'products/drink_cola.png', true, false, null)
    ON CONFLICT (name) DO NOTHING;

    -- Обновляем скидочные цены для акционных товаров
    UPDATE products
    SET discounted_price = ROUND(price * (1 - discount_percent / 100.0), 2)
    WHERE is_special_offer = true AND discounted_price IS NULL;
END
$$;

-- Определяем ID категории "Десерты"
DO $$
DECLARE
    desserts_category_id INTEGER;
BEGIN
    SELECT id INTO desserts_category_id FROM categories WHERE name = 'Десерты';

    -- Вставка данных о продуктах с указанием BigDecimal для цены
    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Десерт "Шоколадный"', 'Шоколадный десерт', 499.00, 450, desserts_category_id, 'products/dessert_chocolate.png', true, false, null)
    ON CONFLICT (name) DO NOTHING;

    -- Обновляем скидочные цены для акционных товаров
    UPDATE products
    SET discounted_price = ROUND(price * (1 - discount_percent / 100.0), 2)
    WHERE is_special_offer = true AND discounted_price IS NULL;
END
$$;

-- Определяем ID категории "Закуски"
DO $$
DECLARE
    snacks_category_id INTEGER;
BEGIN
    SELECT id INTO snacks_category_id FROM categories WHERE name = 'Закуски';

    -- Вставка данных о продуктах с указанием BigDecimal для цены
    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Картофель Фри 100 гр.', 'Картофель Фри 100 гр.', 499.00, 450, snacks_category_id, 'products/free.png', true, false, null),
        ('Картофель Фри 150 гр.', 'СКартофель Фри 150 гр.', 499.00, 450, snacks_category_id, 'products/free.png', true, false, null),
        ('Нагецы 6 штук', 'Нагецы 6 штук', 599.00, 480, snacks_category_id, 'products/nagets.png', true, true, 15),
        ('Нагецы 9 штук', 'Нагецы 9 штук', 599.00, 480, snacks_category_id, 'products/nagets.png', true, true, 15),
        ('Нагецы 12 штук', 'Нагецы 12 штук', 599.00, 480, snacks_category_id, 'products/nagets.png', true, true, 15),
        ('Стрипсы 6 штук', 'Стрипсы 6 штук', 549.00, 470, snacks_category_id, 'products/strips.png', true, false, null),
        ('Стрипсы 9 штук', 'Стрипсы 9 штук', 549.00, 470, snacks_category_id, 'products/strips.png', true, false, null),
        ('Крылья 6 штук', 'Крылья 6 штук', 679.00, 490, snacks_category_id, 'products/chiken.png', true, true, 12),
        ('Крылья 9 штук', 'Крылья 9 штук', 679.00, 490, snacks_category_id, 'products/chiken.png', true, true, 12),
        ('Соус Кисло-сладкий', 'Соус Кисло-сладкий', 40.00, 490, snacks_category_id, 'products/soyse_kislo.png', true, true, 12),
        ('Соус Сырный', 'Соус Сырный', 40.00, 490, snacks_category_id, 'products/soyse_chees.png', true, true, 12),
        ('Соус барбекю', 'Соус барбекю', 40.00, 490, snacks_category_id, 'products/soyse_barbeq.png', true, true, 12),
        ('Кетчуп', 'Кетчуп', 40.00, 490, snacks_category_id, 'products/soyse_ketchyp.png', true, true, 12)
    ON CONFLICT (name) DO NOTHING;

    -- Обновляем скидочные цены для акционных товаров
    UPDATE products
    SET discounted_price = ROUND(price * (1 - discount_percent / 100.0), 2)
    WHERE is_special_offer = true AND discounted_price IS NULL;
END
$$;

-- Определяем ID категории "Салаты"

DO $$
DECLARE
    salads_category_id INTEGER;
BEGIN
    SELECT id INTO salads_category_id FROM categories WHERE name = 'Салаты';

    -- Вставка данных о продуктах с указанием BigDecimal для цены
    INSERT INTO products (name, description, price, weight, category_id, image_url, is_available, is_special_offer, discount_percent)
    VALUES
        ('Салат "Цезарь"', 'Салат с курицей и сыром', 499.00, 450, salads_category_id, 'products/salad_caesar.png', true, false, null),
        ('Салат "Оливье"', 'Салат с курицей и сыром', 599.00, 480, salads_category_id, 'products/salad_olive.png', true, true, 15),
        ('Салат "Греческий"', 'Салат с курицей и сыром', 549.00, 470, salads_category_id, 'products/salad_greek.png', true, false, null),
        ('Салат "Русский"', 'Салат с курицей и сыром', 679.00, 490, salads_category_id, 'products/salad_russian.png', true, true, 12)
    ON CONFLICT (name) DO NOTHING;

    -- Обновляем скидочные цены для акционных товаров
    UPDATE products
    SET discounted_price = ROUND(price * (1 - discount_percent / 100.0), 2)
    WHERE is_special_offer = true AND discounted_price IS NULL;
END
$$;

-- Добавление пунктов выдачи
INSERT INTO
    delivery_locations (
        name,
        address,
        latitude,
        longitude,
        is_active
    )
VALUES (
        'Пункт выдачи #1',
        'ул. Шестакова 1Б',
        55.7558,
        37.6173,
        true
    ),
    (
        'Пункт выдачи #2',
        'ул. Шестакова, 1Б',
        55.7539,
        37.6208,
        true
    ) ON CONFLICT (name) DO NOTHING;