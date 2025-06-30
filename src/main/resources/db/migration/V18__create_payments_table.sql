-- Создание таблицы платежей для интеграции с ЮKassa
-- V22__create_payments_table.sql

-- Создание таблицы платежей
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    yookassa_payment_id VARCHAR(255) UNIQUE,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    method VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
    bank_id VARCHAR(100),
    confirmation_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    paid_at TIMESTAMP,
    error_message TEXT,

-- Дополнительные поля для ЮKassa
idempotence_key VARCHAR(255),
    metadata TEXT,
    receipt_url VARCHAR(500),
    refund_id VARCHAR(255)
);

-- Индексы для производительности
CREATE INDEX idx_payments_yookassa_payment_id ON payments (yookassa_payment_id);

CREATE INDEX idx_payments_order_id ON payments (order_id);

CREATE INDEX idx_payments_status ON payments (status);

CREATE INDEX idx_payments_method ON payments (method);

CREATE INDEX idx_payments_created_at ON payments (created_at);

CREATE INDEX idx_payments_bank_id ON payments (bank_id)
WHERE
    bank_id IS NOT NULL;

-- Добавляем поле payment_status для заказов (если еще не существует)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'orders' AND column_name = 'payment_status') THEN
        ALTER TABLE orders ADD COLUMN payment_status VARCHAR(50) DEFAULT 'UNPAID';

CREATE INDEX idx_orders_payment_status ON orders (payment_status);

END IF;

END $$;

-- Комментарии к таблице и полям
COMMENT ON TABLE payments IS 'Таблица платежей через ЮKassa API';

COMMENT ON COLUMN payments.yookassa_payment_id IS 'ID платежа в системе ЮKassa';

COMMENT ON COLUMN payments.order_id IS 'Ссылка на заказ';

COMMENT ON COLUMN payments.status IS 'Статус платежа: PENDING, WAITING_FOR_CAPTURE, SUCCEEDED, CANCELED, FAILED';

COMMENT ON COLUMN payments.method IS 'Метод оплаты: SBP, BANK_CARD, YOOMONEY и др.';

COMMENT ON COLUMN payments.bank_id IS 'ID банка для СБП платежей (sberbank, tinkoff, vtb и др.)';

COMMENT ON COLUMN payments.confirmation_url IS 'URL для подтверждения платежа';

COMMENT ON COLUMN payments.idempotence_key IS 'Ключ идемпотентности для ЮKassa';

COMMENT ON COLUMN payments.metadata IS 'Дополнительные данные в формате JSON';

COMMENT ON COLUMN payments.receipt_url IS 'URL чека об оплате';

COMMENT ON COLUMN payments.refund_id IS 'ID возврата в случае отмены платежа';