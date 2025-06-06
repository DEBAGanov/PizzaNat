-- Миграция V12: Добавление SMS аутентификации
-- Создание таблицы для SMS кодов и обновление таблицы пользователей

-- Создание таблицы для хранения SMS кодов
CREATE TABLE IF NOT EXISTS sms_codes (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    code VARCHAR(4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    attempts INTEGER DEFAULT 0
);

-- Создание индексов для оптимизации запросов
CREATE INDEX IF NOT EXISTS idx_sms_codes_phone_number ON sms_codes (phone_number);

CREATE INDEX IF NOT EXISTS idx_sms_codes_expires_at ON sms_codes (expires_at);

CREATE INDEX IF NOT EXISTS idx_sms_codes_phone_used_expires ON sms_codes (
    phone_number,
    used,
    expires_at
);

-- Добавление полей для SMS аутентификации в таблицу users
ALTER TABLE users
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20) UNIQUE;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS is_phone_verified BOOLEAN DEFAULT FALSE;

-- Разрешение NULL значений для email (для пользователей, регистрирующихся только по SMS)
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- Комментарии для документации
COMMENT ON
TABLE sms_codes IS 'Таблица для хранения SMS кодов аутентификации';

COMMENT ON COLUMN sms_codes.phone_number IS 'Номер телефона в формате +7XXXXXXXXXX';

COMMENT ON COLUMN sms_codes.code IS '4-значный код для подтверждения';

COMMENT ON COLUMN sms_codes.expires_at IS 'Время истечения кода (TTL 10 минут)';

COMMENT ON COLUMN sms_codes.used IS 'Флаг использования кода';

COMMENT ON COLUMN sms_codes.attempts IS 'Количество попыток ввода кода';

COMMENT ON COLUMN users.phone_number IS 'Номер телефона пользователя для SMS аутентификации';

COMMENT ON COLUMN users.is_phone_verified IS 'Флаг подтверждения номера телефона';