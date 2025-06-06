-- Миграция V13: Добавление Telegram аутентификации
-- Создание таблицы для Telegram токенов и обновление таблицы пользователей

-- Создание таблицы для хранения Telegram auth токенов
CREATE TABLE IF NOT EXISTS telegram_auth_tokens (
    id BIGSERIAL PRIMARY KEY,
    auth_token VARCHAR(50) UNIQUE NOT NULL,
    telegram_id BIGINT,
    telegram_username VARCHAR(100),
    telegram_first_name VARCHAR(100),
    telegram_last_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    device_id VARCHAR(255)
);

-- Создание индексов для оптимизации запросов
CREATE INDEX IF NOT EXISTS idx_telegram_auth_tokens_auth_token ON telegram_auth_tokens (auth_token);

CREATE INDEX IF NOT EXISTS idx_telegram_auth_tokens_expires_at ON telegram_auth_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_telegram_auth_tokens_status ON telegram_auth_tokens (status);

CREATE INDEX IF NOT EXISTS idx_telegram_auth_tokens_telegram_id ON telegram_auth_tokens (telegram_id);

-- Добавление полей для Telegram аутентификации в таблицу users
ALTER TABLE users ADD COLUMN IF NOT EXISTS telegram_id BIGINT UNIQUE;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS telegram_username VARCHAR(100);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS is_telegram_verified BOOLEAN DEFAULT FALSE;

-- Комментарии для документации
COMMENT ON
TABLE telegram_auth_tokens IS 'Таблица для хранения Telegram токенов аутентификации';

COMMENT ON COLUMN telegram_auth_tokens.auth_token IS 'Уникальный токен аутентификации с префиксом tg_auth_';

COMMENT ON COLUMN telegram_auth_tokens.telegram_id IS 'ID пользователя в Telegram';

COMMENT ON COLUMN telegram_auth_tokens.telegram_username IS 'Username пользователя в Telegram (без @)';

COMMENT ON COLUMN telegram_auth_tokens.status IS 'Статус токена: PENDING, CONFIRMED, EXPIRED';

COMMENT ON COLUMN telegram_auth_tokens.expires_at IS 'Время истечения токена (TTL 10 минут)';

COMMENT ON COLUMN telegram_auth_tokens.confirmed_at IS 'Время подтверждения аутентификации';

COMMENT ON COLUMN telegram_auth_tokens.device_id IS 'ID устройства для отслеживания (опционально)';

COMMENT ON COLUMN users.telegram_id IS 'ID пользователя в Telegram для аутентификации';

COMMENT ON COLUMN users.telegram_username IS 'Username пользователя в Telegram';

COMMENT ON COLUMN users.is_telegram_verified IS 'Флаг подтверждения Telegram аутентификации';