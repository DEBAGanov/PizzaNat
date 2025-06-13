-- Миграция V14: Создание таблицы для отложенных уведомлений
-- Дата: 2025-06-13
-- Описание: Создание системы отложенных уведомлений для стимулирования рефералов после доставки заказа

-- Создание таблицы для хранения отложенных уведомлений
CREATE TABLE IF NOT EXISTS scheduled_notifications (
    id BIGSERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users (id) ON DELETE CASCADE,
    telegram_id BIGINT,
    notification_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message TEXT
);

-- Создание индексов для оптимизации запросов
CREATE INDEX IF NOT EXISTS idx_scheduled_notifications_scheduled_at ON scheduled_notifications (scheduled_at);

CREATE INDEX IF NOT EXISTS idx_scheduled_notifications_status ON scheduled_notifications (status);

CREATE INDEX IF NOT EXISTS idx_scheduled_notifications_order_id ON scheduled_notifications (order_id);

CREATE INDEX IF NOT EXISTS idx_scheduled_notifications_user_id ON scheduled_notifications (user_id);

CREATE INDEX IF NOT EXISTS idx_scheduled_notifications_telegram_id ON scheduled_notifications (telegram_id);

CREATE INDEX IF NOT EXISTS idx_scheduled_notifications_type ON scheduled_notifications (notification_type);

-- Составной индекс для поиска готовых к отправке уведомлений
CREATE INDEX IF NOT EXISTS idx_scheduled_notifications_ready_to_send ON scheduled_notifications (status, scheduled_at)
WHERE
    status = 'PENDING';

-- Комментарии для документации
COMMENT ON
TABLE scheduled_notifications IS 'Таблица для хранения отложенных уведомлений (реферальные сообщения, напоминания и т.д.)';

COMMENT ON COLUMN scheduled_notifications.order_id IS 'ID заказа, связанного с уведомлением';

COMMENT ON COLUMN scheduled_notifications.user_id IS 'ID пользователя для отправки уведомления';

COMMENT ON COLUMN scheduled_notifications.telegram_id IS 'Telegram ID пользователя (если доступен)';

COMMENT ON COLUMN scheduled_notifications.notification_type IS 'Тип уведомления: REFERRAL_REMINDER, ORDER_FEEDBACK, etc.';

COMMENT ON COLUMN scheduled_notifications.message IS 'Текст сообщения для отправки';

COMMENT ON COLUMN scheduled_notifications.scheduled_at IS 'Время запланированной отправки';

COMMENT ON COLUMN scheduled_notifications.sent_at IS 'Время фактической отправки';

COMMENT ON COLUMN scheduled_notifications.status IS 'Статус: PENDING, SENT, FAILED, CANCELLED';

COMMENT ON COLUMN scheduled_notifications.retry_count IS 'Количество попыток отправки';

COMMENT ON COLUMN scheduled_notifications.max_retries IS 'Максимальное количество попыток';

COMMENT ON COLUMN scheduled_notifications.error_message IS 'Сообщение об ошибке при неудачной отправке';