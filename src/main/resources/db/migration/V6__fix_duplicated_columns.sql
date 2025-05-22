-- Исправление дублирующихся столбцов и добавление NOT NULL ограничений

-- Исправление дублированных столбцов в таблице delivery_locations
DO $$
DECLARE
    col_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO col_count
    FROM information_schema.columns
    WHERE table_name = 'delivery_locations' AND column_name = 'active';
    
    IF col_count > 1 THEN
        ALTER TABLE delivery_locations DROP COLUMN active;
        ALTER TABLE delivery_locations ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
    END IF;
END $$;

-- Исправление дублированных столбцов в таблице order_statuses
DO $$
DECLARE
    col_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO col_count
    FROM information_schema.columns
    WHERE table_name = 'order_statuses' AND column_name = 'description';
    
    IF col_count > 1 THEN
        ALTER TABLE order_statuses DROP COLUMN description;
        ALTER TABLE order_statuses ADD COLUMN description VARCHAR(255) NOT NULL;
        UPDATE order_statuses SET description = name;
    END IF;
END $$;

-- Исправление дублированных столбцов в таблице products
DO $$
DECLARE
    col_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO col_count
    FROM information_schema.columns
    WHERE table_name = 'products' AND column_name = 'available';
    
    IF col_count > 1 THEN
        ALTER TABLE products DROP COLUMN available;
        ALTER TABLE products ADD COLUMN available BOOLEAN NOT NULL DEFAULT true;
    END IF;
END $$;