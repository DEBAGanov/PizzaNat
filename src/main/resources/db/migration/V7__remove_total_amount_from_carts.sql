-- First, set a default value for existing records and then remove the not null constraint
-- Remove total_amount column from carts table as it will be calculated dynamically
ALTER TABLE carts ALTER COLUMN total_amount DROP NOT NULL;

ALTER TABLE carts DROP COLUMN total_amount;