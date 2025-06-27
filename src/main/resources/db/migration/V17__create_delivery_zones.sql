-- Создание системы зон доставки для города Волжск
-- Описание: Добавляет возможность настройки разных тарифов для разных районов города

-- Основная таблица зон доставки
CREATE TABLE delivery_zones (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL, -- Название зоны
    description TEXT, -- Описание зоны
    base_cost DECIMAL(10, 2) NOT NULL, -- Базовая стоимость доставки
    free_delivery_threshold DECIMAL(10, 2), -- Сумма бесплатной доставки
    delivery_time_min INTEGER DEFAULT 30, -- Минимальное время доставки (мин)
    delivery_time_max INTEGER DEFAULT 45, -- Максимальное время доставки (мин)
    is_active BOOLEAN DEFAULT TRUE, -- Активность зоны
    priority INTEGER DEFAULT 0, -- Приоритет при перекрытии зон
    color_hex VARCHAR(7) DEFAULT '#3498db', -- Цвет для отображения на карте
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Улицы, входящие в зоны доставки
CREATE TABLE delivery_zone_streets (
    id SERIAL PRIMARY KEY,
    zone_id INTEGER REFERENCES delivery_zones (id) ON DELETE CASCADE,
    street_name VARCHAR(255) NOT NULL, -- Название улицы
    house_number_from INTEGER, -- Дома с номера (если null - вся улица)
    house_number_to INTEGER, -- Дома до номера (если null - до конца)
    is_even_only BOOLEAN DEFAULT FALSE, -- Только четные дома
    is_odd_only BOOLEAN DEFAULT FALSE, -- Только нечетные дома
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ключевые слова для определения зон (микрорайоны, районы)
CREATE TABLE delivery_zone_keywords (
    id SERIAL PRIMARY KEY,
    zone_id INTEGER REFERENCES delivery_zones (id) ON DELETE CASCADE,
    keyword VARCHAR(255) NOT NULL, -- "мкр", "микрорайон", "промзона"
    match_type VARCHAR(20) DEFAULT 'contains', -- 'contains', 'starts_with', 'exact'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Добавление реальных районов города Волжск
INSERT INTO
    delivery_zones (
        name,
        description,
        base_cost,
        free_delivery_threshold,
        delivery_time_min,
        delivery_time_max,
        priority,
        color_hex
    )
VALUES

-- ЖИЛЫЕ РАЙОНЫ (9 районов)
(
    'Центральный',
    'Центральная часть города, административный центр',
    200.00,
    1000.00,
    25,
    35,
    1,
    '#2ecc71'
),
(
    'Заря',
    'Район Заря с характерными названиями улиц',
    250.00,
    1200.00,
    30,
    40,
    2,
    '#f39c12'
),
(
    'Машиностроитель',
    'Район машиностроителей и рабочих специальностей',
    200.00,
    1000.00,
    25,
    35,
    3,
    '#3498db'
),
(
    'Дружба',
    'Самый доступный район для доставки',
    100.00,
    800.00,
    20,
    30,
    4,
    '#27ae60'
),
(
    'ВДК',
    'Военный городок и авиационные улицы',
    200.00,
    1000.00,
    25,
    35,
    5,
    '#9b59b6'
),
(
    'Северный',
    'Северная часть города',
    200.00,
    1000.00,
    30,
    40,
    6,
    '#34495e'
),
(
    'Горгаз',
    'Район газового хозяйства и коммунальных служб',
    200.00,
    1000.00,
    25,
    35,
    7,
    '#16a085'
),
(
    'Луговая',
    'Луговые районы города',
    250.00,
    1200.00,
    35,
    45,
    8,
    '#e67e22'
),
(
    'Мамасево',
    'Район Мамасево',
    250.00,
    1200.00,
    35,
    45,
    9,
    '#e74c3c'
),

-- ПРОМЫШЛЕННЫЕ РАЙОНЫ (2 района)
(
    'Прибрежный',
    'Прибрежная промышленная зона',
    300.00,
    1500.00,
    40,
    55,
    10,
    '#95a5a6'
),
(
    'Промузел',
    'Промышленный узел',
    300.00,
    1500.00,
    40,
    55,
    11,
    '#7f8c8d'
);

-- Добавление улиц для каждого района

-- 1. ЦЕНТРАЛЬНЫЙ РАЙОН (200₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Ленина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Советская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Комсомольская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Первомайская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Карла Маркса'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Мира'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Кирова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Свободы'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Калинина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Красная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Красноармейская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Коммунистическая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Интернациональная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Куйбышева'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Фрунзе'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Дзержинского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Либкнехта'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Люксембург'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Люксембург 1-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Люксембург 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Энгельса'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Коммунаров'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Пролетарская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Степана Разина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Парижской Коммуны'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Халтурина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Щорса'
    ),
    -- Писатели и поэты
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Пушкина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Лермонтова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Гоголя'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Некрасова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Толстого'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Чехова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Горького'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Маяковского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Грибоедова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Островского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Жуковского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Герцена'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Белинского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Чернышевского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Кольцова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Крылова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Пугачева'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Шевченко'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Шкетана'
    ),
    -- Именные улицы
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'имени Виктора Григорьевича Васильева'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'имени Субботина Ивана Павловича'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Иванова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Елисеева'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Гаврилова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Серова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Володарского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Свердлова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Щербакова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Мухина'
    ),
    -- Праздничные улицы
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        '8 Марта'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        '9 Января'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Красный Дол'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Центральный'
        ),
        'Краснофлотская'
    );

-- 2. ЗАРЯ РАЙОН (250₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заря'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заринская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        '1-я Заринская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        '2-я Заринская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        '3-я Заринская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        '4-я Заринская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        '5-я Заринская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заречная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заречная 1-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заречная 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заречная 3-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Зеленая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Зеленый Бор'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Западная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Западная Луговая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заводская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Залесная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Залесная 1-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Залесная 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Залесная 3-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Заря'
        ),
        'Заячья'
    );

-- 3. МАШИНОСТРОИТЕЛЬ РАЙОН (200₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Машиностроителей'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        '2-я Машиностроителей'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        '3-я Машиностроителей'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Металлургов'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Энтузиастов'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Стахановская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Строительная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Техникумовская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Транспортная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Бумажников'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        '107 Бригады'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Орджоникидзе'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Матросова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Матюшенко'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Баумана'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Машиностроитель'
        ),
        'Крайняя'
    );

-- 4. ДРУЖБА РАЙОН (100₽) - самый доступный
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Дружбы'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Молодежная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Пионерская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Спортивная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Фестивальная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Юбилейная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Майская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Весенняя'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Солнечная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Светлая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Счастливая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Цветочная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Букетная'
    ),
    -- Цветочные улицы
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Маковая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Изумрудная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Янтарная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        'Ясная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        '1-я Ясная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        '2-я Ясная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Дружба'
        ),
        '3-я Ясная'
    );

-- 5. ВДК РАЙОН (200₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Гагарина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Чапаева'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Чкалова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Громова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Папанина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Байдукова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Леваневского'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Комарова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Авиации'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Осипенко'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Андрея Баранова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Федина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Кузьмина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'ВДК'
        ),
        'Шестакова'
    );

-- 6. СЕВЕРНЫЙ РАЙОН (200₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Северная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Нагорная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Горная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Верхне-Луговая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Нижне-Луговая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Полевая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Степная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Придорожная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Пограничная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Крылова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Новая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Новая 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Тихая'
    ),
    -- Лесные улицы
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Лесная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Лесная 1-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Лесная 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Хвойная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Сосновая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Сосновый Бор'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Еловая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Кедровая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Пихтовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Северный'
        ),
        'Дубравная'
    );

-- 7. ГОРГАЗ РАЙОН (200₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Волжская подстанция'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Кооперативная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Совхозная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Колхозная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Промбаза'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Учительская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Вавилова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Тимирязева'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Мичурина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Горгаз'
        ),
        'Ломоносова'
    );

-- 8. ЛУГОВАЯ РАЙОН (250₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Луговая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        '1-ая Луговая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Луговая 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Луговая 3-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Луговая 4-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Русская Луговая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Овражная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Палантая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Парковая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Парковская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Парники'
    ),
    -- Садово-парковые улицы
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Садовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Березовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Дубовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Дубовая 1-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        '2-я Дубовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Липовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Ольховая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Осиновая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Рябиновая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Каштановая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Кленовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Кленовая 1-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Кленовая 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Кленовая 3-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Кленовая 4-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Кленовая 5-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        '6-я Кленовая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Луговая'
        ),
        'Лиственная'
    );

-- 9. МАМАСЕВО РАЙОН (250₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Мамасево'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Ямулова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Помарское шоссе'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Йошкар-Олинское шоссе'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Кабанова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Кошкина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Маркина'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Орлова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Прохорова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Мосолова'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Озерки'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Озерная'
    ),
    -- Ягодные улицы
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Вишнёвая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Черёмуховая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Брусничная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Ежевичная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Малиновая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Мамасево'
        ),
        'Ореховая'
    );

-- 10. ПРИБРЕЖНЫЙ ПРОМЫШЛЕННЫЙ РАЙОН (300₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Береговая'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Набережная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Волга'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Волжская'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Воложка'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Вокзальная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Железнодорожная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        '11 км ж/д'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Прибрежный'
        ),
        'Лесозаводская'
    );

-- 11. ПРОМУЗЕЛ ПРОМЫШЛЕННЫЙ РАЙОН (300₽)
INSERT INTO
    delivery_zone_streets (zone_id, street_name)
VALUES (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Промузел'
        ),
        'Промузел'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Промузел'
        ),
        'Промышленная'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Промузел'
        ),
        'Промышленная 1-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Промузел'
        ),
        'Промышленная 2-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Промузел'
        ),
        'Промышленная 3-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Промузел'
        ),
        'Промышленная 4-я'
    ),
    (
        (
            SELECT id
            FROM delivery_zones
            WHERE
                name = 'Промузел'
        ),
        'Промышленная 5-я'
    );

-- Добавление ключевых слов для определения районов
INSERT INTO
    delivery_zone_keywords (zone_id, keyword, match_type)
VALUES

-- Заря район - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Заря'
    ),
    'заря',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Заря'
    ),
    'зарин',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Заря'
    ),
    'заречн',
    'contains'
),

-- Машиностроитель - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Машиностроитель'
    ),
    'машиностроител',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Машиностроитель'
    ),
    'металлург',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Машиностроитель'
    ),
    'бригады',
    'contains'
),

-- Дружба - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Дружба'
    ),
    'дружб',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Дружба'
    ),
    'молодеж',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Дружба'
    ),
    'пионер',
    'contains'
),

-- ВДК - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'ВДК'
    ),
    'авиаци',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'ВДК'
    ),
    'гагарин',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'ВДК'
    ),
    'чкалов',
    'contains'
),

-- Северный - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Северный'
    ),
    'северн',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Северный'
    ),
    'лесн',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Северный'
    ),
    'сосн',
    'contains'
),

-- Луговая - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Луговая'
    ),
    'лугов',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Луговая'
    ),
    'парков',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Луговая'
    ),
    'садов',
    'contains'
),

-- Мамасево - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Мамасево'
    ),
    'мамасево',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Мамасево'
    ),
    'помарск',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Мамасево'
    ),
    'йошкар',
    'contains'
),

-- Прибрежный - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Прибрежный'
    ),
    'прибрежн',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Прибрежный'
    ),
    'волжск',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Прибрежный'
    ),
    'железнодорожн',
    'contains'
),

-- Промузел - ключевые слова
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Промузел'
    ),
    'промузел',
    'contains'
),
(
    (
        SELECT id
        FROM delivery_zones
        WHERE
            name = 'Промузел'
    ),
    'промышленн',
    'contains'
);

-- Обновление комментариев
COMMENT ON
TABLE delivery_zones IS 'Реальные районы города Волжск с тарифами доставки (9 жилых + 2 промышленных)';

COMMENT ON
TABLE delivery_zone_streets IS 'Все улицы, переулки и проезды города Волжск с привязкой к районам';

COMMENT ON
TABLE delivery_zone_keywords IS 'Ключевые слова для автоматического определения районов по адресу';

-- Индексы для оптимизации поиска
CREATE INDEX idx_delivery_zone_streets_name ON delivery_zone_streets (street_name);

CREATE INDEX idx_delivery_zone_keywords_keyword ON delivery_zone_keywords (keyword);

CREATE INDEX idx_delivery_zones_active ON delivery_zones (is_active);

CREATE INDEX idx_delivery_zones_priority ON delivery_zones (priority DESC);

-- Комментарии к таблицам
COMMENT ON
TABLE delivery_zones IS 'Зоны доставки с различными тарифами';

COMMENT ON
TABLE delivery_zone_streets IS 'Улицы, входящие в зоны доставки';

COMMENT ON
TABLE delivery_zone_keywords IS 'Ключевые слова для определения зон доставки';

COMMENT ON COLUMN delivery_zones.priority IS 'Приоритет зоны (больше = выше приоритет при пересечении)';

COMMENT ON COLUMN delivery_zone_streets.house_number_from IS 'Дома с номера (NULL = вся улица)';

COMMENT ON COLUMN delivery_zone_streets.house_number_to IS 'Дома до номера (NULL = до конца улицы)';

COMMENT ON COLUMN delivery_zone_keywords.match_type IS 'Тип сопоставления: contains, starts_with, exact';