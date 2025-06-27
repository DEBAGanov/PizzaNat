# 🗺️ Руководство по настройке зональной системы доставки

## 📋 Обзор

Зональная система доставки позволяет настраивать разные тарифы для различных районов города. Система поддерживает:

- ✅ **Гибкое определение зон** по улицам и ключевым словам
- ✅ **Различные тарифы** для разных районов города  
- ✅ **Бесплатную доставку** с индивидуальными порогами по зонам
- ✅ **Время доставки** настраиваемое для каждой зоны
- ✅ **Приоритеты зон** при пересечении территорий

## 🏗️ Архитектура системы

### Основные компоненты:

1. **`delivery_zones`** - Основные зоны с тарифами
2. **`delivery_zone_streets`** - Привязка улиц к зонам
3. **`delivery_zone_keywords`** - Ключевые слова для определения зон
4. **`DeliveryZoneService`** - Сервис расчета доставки
5. **`DeliveryController`** - API эндпоинты

## 📊 Структура данных

### delivery_zones
```sql
CREATE TABLE delivery_zones (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,                     -- "Центральная зона"
    description TEXT,                               -- "Центр города"
    base_cost DECIMAL(10,2) NOT NULL,              -- 150.00
    free_delivery_threshold DECIMAL(10,2),         -- 800.00
    delivery_time_min INTEGER DEFAULT 30,          -- 20
    delivery_time_max INTEGER DEFAULT 45,          -- 30
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,                    -- Чем больше, тем выше приоритет
    color_hex VARCHAR(7) DEFAULT '#3498db'         -- Цвет для админки
);
```

### delivery_zone_streets
```sql
CREATE TABLE delivery_zone_streets (
    id SERIAL PRIMARY KEY,
    zone_id INTEGER REFERENCES delivery_zones(id),
    street_name VARCHAR(255) NOT NULL,             -- "Ленина"
    house_number_from INTEGER,                     -- 1 (необязательно)
    house_number_to INTEGER,                       -- 50 (необязательно)
    is_even_only BOOLEAN DEFAULT FALSE,            -- Только четные дома
    is_odd_only BOOLEAN DEFAULT FALSE              -- Только нечетные дома
);
```

### delivery_zone_keywords
```sql
CREATE TABLE delivery_zone_keywords (
    id SERIAL PRIMARY KEY,
    zone_id INTEGER REFERENCES delivery_zones(id),
    keyword VARCHAR(255) NOT NULL,                 -- "мкр", "СНТ", "промзона"
    match_type VARCHAR(20) DEFAULT 'contains'      -- contains, starts_with, exact
);
```

## 🛠️ Настройка зон для вашего города

### Шаг 1: Создание основных зон

```sql
-- Пример для города Волжск
INSERT INTO delivery_zones (name, description, base_cost, free_delivery_threshold, delivery_time_min, delivery_time_max, priority) VALUES
('Центральная зона', 'Центр города с основными улицами', 150.00, 800.00, 20, 30, 1),
('Жилые районы', 'Микрорайоны и спальные районы', 200.00, 1000.00, 30, 45, 2),
('Удаленные районы', 'Частный сектор и промзоны', 300.00, 1500.00, 45, 60, 3);
```

### Шаг 2: Добавление улиц

```sql
-- Центральные улицы (зона ID = 1)
INSERT INTO delivery_zone_streets (zone_id, street_name) VALUES
(1, 'Ленина'),
(1, 'Советская'),
(1, 'Комсомольская');

-- Жилые улицы (зона ID = 2)  
INSERT INTO delivery_zone_streets (zone_id, street_name) VALUES
(2, 'Октябрьская'),
(2, 'Пионерская'),
(2, 'Молодежная');

-- Пример с диапазоном домов
INSERT INTO delivery_zone_streets (zone_id, street_name, house_number_from, house_number_to) VALUES
(1, 'Мира', 1, 100);  -- ул. Мира дома 1-100 в центральной зоне
```

### Шаг 3: Добавление ключевых слов

```sql
-- Удаленные районы (зона ID = 3)
INSERT INTO delivery_zone_keywords (zone_id, keyword, match_type) VALUES
(3, 'Промышленная', 'contains'),
(3, 'мкр', 'contains'),
(3, 'СНТ', 'contains'),
(3, 'Дачная', 'starts_with');
```

## 🎯 Логика определения зон

### Алгоритм работы:

1. **Поиск по улицам**: Проверяется соответствие названия улицы и номера дома
2. **Поиск по ключевым словам**: Проверяется наличие ключевых слов в адресе
3. **Приоритет**: При пересечении зон выбирается зона с наибольшим приоритетом
4. **Fallback**: Если зона не найдена - доставка недоступна

### Примеры сопоставления:

```
"г. Волжск, ул. Ленина, д. 5"     → Центральная зона (150₽)
"г. Волжск, ул. Октябрьская, 10"  → Жилые районы (200₽)  
"г. Волжск, мкр Южный"            → Удаленные районы (300₽)
"г. Волжск, СНТ Дружба"           → Удаленные районы (300₽)
"г. Москва, Красная площадь"      → Вне зоны (недоступна)
```

## 📡 API Использование

### Расчет стоимости доставки

```bash
# Базовый запрос
GET /api/v1/delivery/estimate?address=г.%20Волжск,%20ул.%20Ленина,%20д.%201

# С суммой заказа для расчета скидок
GET /api/v1/delivery/estimate?address=г.%20Волжск,%20ул.%20Ленина,%20д.%201&orderAmount=900
```

### Ответ API

```json
{
  "address": "г. Волжск, ул. Ленина, д. 1",
  "deliveryAvailable": true,
  "zoneName": "Центральная зона",
  "zoneDescription": "Центр города с основными улицами",
  "deliveryCost": 0,          // 0 т.к. 900₽ > 800₽ (бесплатная доставка)
  "baseCost": 150,            // Базовая стоимость зоны
  "freeDeliveryThreshold": 800,
  "isDeliveryFree": true,
  "estimatedTime": "20-30 минут",
  "estimatedTimeMin": 20,
  "estimatedTimeMax": 30,
  "message": "Бесплатная доставка",
  "currency": "RUB",
  "workingHours": "09:00-22:00",
  "city": "Волжск",
  "region": "Республика Марий Эл"
}
```

## 🧪 Тестирование

### Запуск тестов зональной системы

```bash
# Сделать файл исполняемым
chmod +x test_delivery_zones.sh

# Запустить тесты
./test_delivery_zones.sh
```

### Ожидаемые результаты:

- ✅ **13 тестов** для всех зон и граничных случаев
- ✅ **95%+ успешность** для корректно настроенной системы
- ✅ **Проверка расчета** стоимости и бесплатной доставки

## ⚙️ Администрирование

### Изменение стоимости доставки

```sql
-- Обновить базовую стоимость для зоны
UPDATE delivery_zones 
SET base_cost = 180.00 
WHERE name = 'Центральная зона';

-- Изменить порог бесплатной доставки
UPDATE delivery_zones 
SET free_delivery_threshold = 1200.00 
WHERE name = 'Жилые районы';
```

### Добавление новой улицы

```sql
-- Добавить улицу в существующую зону
INSERT INTO delivery_zone_streets (zone_id, street_name) 
VALUES (1, 'Новая улица');

-- Добавить улицу с диапазоном домов
INSERT INTO delivery_zone_streets (zone_id, street_name, house_number_from, house_number_to) 
VALUES (2, 'Длинная улица', 1, 50);
```

### Отключение зоны

```sql
-- Временно отключить зону
UPDATE delivery_zones 
SET is_active = false 
WHERE name = 'Удаленные районы';
```

## 🔧 Настройка приоритетов

При пересечении зон используется приоритет:

```sql
-- Более высокий приоритет = более важная зона
UPDATE delivery_zones SET priority = 10 WHERE name = 'Центральная зона';    -- Высший
UPDATE delivery_zones SET priority = 5  WHERE name = 'Жилые районы';         -- Средний  
UPDATE delivery_zones SET priority = 1  WHERE name = 'Удаленные районы';     -- Низкий
```

## 🎨 Цвета для админки

```sql
-- Назначить цвета зонам для отображения на карте
UPDATE delivery_zones SET color_hex = '#2ecc71' WHERE name = 'Центральная зона';    -- Зеленый
UPDATE delivery_zones SET color_hex = '#3498db' WHERE name = 'Жилые районы';         -- Синий
UPDATE delivery_zones SET color_hex = '#e74c3c' WHERE name = 'Удаленные районы';     -- Красный
```

## 📈 Мониторинг и аналитика

### Полезные запросы

```sql
-- Статистика заказов по зонам
SELECT dz.name, COUNT(o.id) as orders_count, AVG(o.total_amount) as avg_amount
FROM orders o
JOIN delivery_locations dl ON o.delivery_location_id = dl.id  
JOIN delivery_zones dz ON ... -- логика определения зоны
GROUP BY dz.name;

-- Самые популярные улицы
SELECT dzs.street_name, COUNT(*) as usage_count  
FROM delivery_zone_streets dzs
-- JOIN с таблицей заказов
GROUP BY dzs.street_name
ORDER BY usage_count DESC;
```

## ⚠️ Важные замечания

1. **Регистронезависимость**: Поиск улиц и ключевых слов происходит без учета регистра
2. **Диапазоны домов**: Если не указаны - вся улица входит в зону
3. **Приоритеты**: При пересечении зон выбирается зона с наибольшим приоритетом
4. **Производительность**: Индексы созданы для оптимизации поиска
5. **Fallback**: Для неизвестных адресов доставка недоступна

## 🚀 Расширение функциональности

### Будущие улучшения:

- 📍 **Геозоны**: Поддержка полигонов для точного определения границ
- 🕒 **Временные зоны**: Разные тарифы в зависимости от времени суток
- 📊 **Динамические тарифы**: Изменение стоимости в зависимости от загруженности
- 🎯 **ML определение**: Машинное обучение для улучшения сопоставления адресов

---

*Документация актуальна на 2025-01-23* 