-- Миграция V15: Исправление пользователей Telegram без email
-- Дата: 2025-06-13
-- Описание: Добавление email для существующих пользователей Telegram для совместимости с мобильным приложением

-- Обновляем пользователей с Telegram ID, у которых нет email
UPDATE users
SET
    email = 'tg_' || telegram_id || '@telegram.pizzanat.local',
    updated_at = CURRENT_TIMESTAMP
WHERE
    telegram_id IS NOT NULL
    AND (
        email IS NULL
        OR email = ''
    );

-- Активируем всех пользователей с подтвержденным Telegram
UPDATE users
SET
    is_active = true,
    updated_at = CURRENT_TIMESTAMP
WHERE
    telegram_id IS NOT NULL
    AND is_telegram_verified = true
    AND is_active = false;