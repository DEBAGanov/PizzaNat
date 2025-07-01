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
        ('Пицца Маргарита', 'Классическая итальянская пицца с томатным соусом и сыром', 499.00, 450, pizza_category_id, 'products/pizza_margarita.png', true, false, null),
        ('Пицца Пепперони', 'Острая пицца с пепперони и сыром', 1.00, 480, pizza_category_id, 'products/pizza_peperoni.png', true, true, 15),
        ('Гавайская пицца', 'Пицца с ветчиной и ананасами', 1.00, 470, pizza_category_id, 'products/pizza_gavaiyaskay.png', true, false, null),
        ('Сырная пицца', 'Пицца с четырьмя видами сыра', 1.00, 460, pizza_category_id, 'products/pizza_4_chees.png', true, false, null),
        ('Пицца 5 сыров', 'Пицца с пятью сортами сыра', 1.00, 490, pizza_category_id, 'products/pizza_5_chees.png', true, true, 10),
        ('Мясная пицца', 'Пицца с ассорти из мясных ингредиентов', 649.00, 520, pizza_category_id, 'products/pizza_mzysnay.png', true, false, null),
        ('Пицца Марио', 'Фирменная пицца с беконом и грибами', 1.00, 500, pizza_category_id, 'products/pizza_mario.png', true, true, 15),
        ('Пицца Карбонара', 'Пицца в стиле пасты карбонара', 1.00, 490, pizza_category_id, 'products/pizza_karbonara.png', true, false, null),
        ('Грибная пицца', 'Пицца с шампиньонами и лесными грибами', 559.00, 470, pizza_category_id, 'products/pizza_gribnaya.png', true, false, null),
        ('Пицца Том Ям', 'Острая пицца в стиле тайского супа', 679.00, 490, pizza_category_id, 'products/pizza_tom_yam.png', true, true, 12)
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
        ('Бургер "Классический"', 'Классический бургер с жареным луком и сыром', 1.00, 450, burgers_category_id, 'products/burger_classic.png', true, false, null),
        ('Бургер "Чизбургер"', 'Бургер с сыром и салатом', 599.00, 480, burgers_category_id, 'products/burger_cheeseburger.png', true, true, 15),
        ('Бургер "Гавайский"', 'Бургер с ветчиной и ананасами', 549.00, 470, burgers_category_id, 'products/burger_hawaiian.png', true, false, null),
        ('Бургер "Том Ям"', 'Острый бургер в стиле тайского супа', 679.00, 490, burgers_category_id, 'products/burger_tom_yam.png', true, true, 12)
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
        ('Молочный коктейль', 'Классический колы', 499.00, 450, drinks_category_id, 'products/drink_cola.png', true, false, null),
        ('Шоколадный Молочный коктейль', 'Классический колы', 499.00, 450, drinks_category_id, 'products/drink_cola.png', true, false, null),
        ('Кока-Кола', 'Классический колы', 499.00, 450, drinks_category_id, 'products/drink_cola.png', true, false, null),
        ('Пепси', 'Классический пепси', 599.00, 480, drinks_category_id, 'products/drink_pepsi.png', true, true, 15),
        ('Фанта', 'Классический фанта', 549.00, 470, drinks_category_id, 'products/drink_fant.png', true, false, null),
        ('Спрайт', 'Классический спрайт', 679.00, 490, drinks_category_id, 'products/drink_sprite.png', true, true, 12),
        ('Кофе', 'Классический кофе', 679.00, 490, drinks_category_id, 'products/drink_coffee.png', true, true, 12),
        ('Чай', 'Классический чай', 679.00, 490, drinks_category_id, 'products/drink_tea.png', true, true, 12),
        ('Сок', 'Классический сок', 679.00, 490, drinks_category_id, 'products/drink_juice.png', true, true, 12)
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
        ('Десерт "Шоколадный"', 'Шоколадный десерт', 499.00, 450, desserts_category_id, 'products/dessert_chocolate.png', true, false, null),
        ('Десерт "Фруктовый"', 'Фруктовый десерт', 599.00, 480, desserts_category_id, 'products/dessert_fruit.png', true, true, 15),
        ('Десерт "Ягодный"', 'Ягодный десерт', 549.00, 470, desserts_category_id, 'products/dessert_berry.png', true, false, null),
        ('Десерт "Клубничный"', 'Клубничный десерт', 679.00, 490, desserts_category_id, 'products/dessert_strawberry.png', true, true, 12)
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
        ('Закуска "Сырные шарики"', 'Сырные шарики', 499.00, 450, snacks_category_id, 'products/snack_cheese_balls.png', true, false, null),
        ('Закуска "Сырные шарики"', 'Сырные шарики', 599.00, 480, snacks_category_id, 'products/snack_cheese_balls.png', true, true, 15),
        ('Закуска "Сырные шарики"', 'Сырные шарики', 549.00, 470, snacks_category_id, 'products/snack_cheese_balls.png', true, false, null),
        ('Закуска "Сырные шарики"', 'Сырные шарики', 679.00, 490, snacks_category_id, 'products/snack_cheese_balls.png', true, true, 12)
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
        'ул. Ленина, 10',
        55.7558,
        37.6173,
        true
    ),
    (
        'Пункт выдачи #2',
        'ул. Пушкина, 15',
        55.7539,
        37.6208,
        true
    ) ON CONFLICT (name) DO NOTHING;