-- Обновление NULL значений в таблице delivery_locations
DO $$
BEGIN
    -- Проверяем существование столбца is_active
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'delivery_locations' AND column_name = 'is_active'
    ) THEN
        -- Обновляем is_active
        UPDATE delivery_locations
        SET is_active = true
        WHERE is_active IS NULL;
    END IF;
END $$;

-- Создание столбца active в таблице order_statuses, если он отсутствует
DO $$
BEGIN
    -- Проверяем, существует ли столбец
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'order_statuses' AND column_name = 'active'
    ) THEN
        -- Добавляем столбец active
        ALTER TABLE order_statuses ADD COLUMN active BOOLEAN DEFAULT TRUE;
    ELSE
        -- Обновляем NULL значения, если столбец существует
        UPDATE order_statuses SET active = true WHERE active IS NULL;
    END IF;
END $$;

-- Создание столбца available в таблице products, если он отсутствует
DO $$
BEGIN
    -- Проверяем, существует ли столбец
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'products' AND column_name = 'available'
    ) THEN
        -- Добавляем столбец available
        ALTER TABLE products ADD COLUMN available BOOLEAN DEFAULT TRUE;
    ELSE
        -- Обновляем NULL значения, если столбец существует
        UPDATE products SET available = true WHERE available IS NULL;
    END IF;
END $$;

-- Проверка и обновление NULL значений для delivery_locations
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'delivery_locations' AND column_name = 'active'
    ) THEN
        UPDATE delivery_locations SET active = true WHERE active IS NULL;
    END IF;
END $$;

-- Проверка и обновление NULL значений для order_statuses
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'order_statuses' AND column_name = 'description'
    ) THEN
        UPDATE order_statuses SET description = name WHERE description IS NULL;
    END IF;
END $$;

-- Проверка и обновление NULL значений для products
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'products' AND column_name = 'available'
    ) THEN
        UPDATE products SET available = true WHERE available IS NULL;
    END IF;
END $$;