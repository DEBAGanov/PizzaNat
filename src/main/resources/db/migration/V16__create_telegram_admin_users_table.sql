-- Миграция V16: Создание таблицы для администраторов Telegram бота
-- Дата: 2025-06-13
-- Описание: Создание таблицы telegram_admin_users для хранения информации об администраторах

CREATE TABLE telegram_admin_users (
    id BIGSERIAL PRIMARY KEY,
    telegram_chat_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT true,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Индексы для оптимизации запросов
CREATE INDEX idx_telegram_admin_users_chat_id ON telegram_admin_users (telegram_chat_id);

CREATE INDEX idx_telegram_admin_users_active ON telegram_admin_users (is_active);

CREATE INDEX idx_telegram_admin_users_registered_at ON telegram_admin_users (registered_at);

CREATE INDEX idx_telegram_admin_users_last_activity ON telegram_admin_users (last_activity_at);

-- Комментарии к таблице и столбцам
COMMENT ON
TABLE telegram_admin_users IS 'Администраторы Telegram бота для уведомлений о заказах';

COMMENT ON COLUMN telegram_admin_users.telegram_chat_id IS 'Уникальный Chat ID администратора в Telegram';

COMMENT ON COLUMN telegram_admin_users.username IS 'Username администратора в Telegram';

COMMENT ON COLUMN telegram_admin_users.first_name IS 'Имя администратора';

COMMENT ON COLUMN telegram_admin_users.last_name IS 'Фамилия администратора';

COMMENT ON COLUMN telegram_admin_users.is_active IS 'Активен ли администратор для получения уведомлений';

COMMENT ON COLUMN telegram_admin_users.registered_at IS 'Дата и время регистрации администратора';

COMMENT ON COLUMN telegram_admin_users.last_activity_at IS 'Дата и время последней активности';

COMMENT ON COLUMN telegram_admin_users.created_at IS 'Дата и время создания записи';

COMMENT ON COLUMN telegram_admin_users.updated_at IS 'Дата и время последнего обновления записи';