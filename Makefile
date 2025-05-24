# =================================================================
# PizzaNat Makefile
# =================================================================

.PHONY: help setup-env start stop restart logs clean build test

# Цвета для вывода
GREEN=\033[0;32m
YELLOW=\033[1;33m
RED=\033[0;31m
NC=\033[0m # No Color

help: ## Показать это сообщение помощи
	@echo "🍕 PizzaNat - Команды разработки"
	@echo "================================"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

setup-env: ## Настроить .env файл из шаблона
	@echo "$(GREEN)🔧 Настройка .env файла...$(NC)"
	@./scripts/setup-env.sh

start: ## Запустить все сервисы
	@echo "$(GREEN)🚀 Запуск PizzaNat...$(NC)"
	@docker compose up -d
	@echo "$(GREEN)✅ PizzaNat запущен!$(NC)"
	@echo "API: http://localhost/api/health"
	@echo "Swagger: http://localhost/swagger-ui/index.html"
	@echo "MinIO Console: http://localhost:9001"

stop: ## Остановить все сервисы
	@echo "$(YELLOW)⏹️  Остановка сервисов...$(NC)"
	@docker compose down
	@echo "$(GREEN)✅ Сервисы остановлены$(NC)"

restart: ## Перезапустить все сервисы
	@echo "$(YELLOW)🔄 Перезапуск сервисов...$(NC)"
	@docker compose down
	@docker compose up -d
	@echo "$(GREEN)✅ Сервисы перезапущены$(NC)"

logs: ## Показать логи приложения
	@echo "$(GREEN)📋 Логи приложения:$(NC)"
	@docker compose logs -f app

logs-all: ## Показать логи всех сервисов
	@echo "$(GREEN)📋 Логи всех сервисов:$(NC)"
	@docker compose logs -f

status: ## Показать статус сервисов
	@echo "$(GREEN)📊 Статус сервисов:$(NC)"
	@docker compose ps

health: ## Проверить здоровье приложения
	@echo "$(GREEN)🏥 Проверка здоровья...$(NC)"
	@curl -s http://localhost/api/health || echo "$(RED)❌ Приложение недоступно$(NC)"

build: ## Пересобрать приложение
	@echo "$(GREEN)🔨 Пересборка приложения...$(NC)"
	@docker compose build app --no-cache
	@echo "$(GREEN)✅ Пересборка завершена$(NC)"

clean: ## Очистить все контейнеры и данные
	@echo "$(RED)🧹 Очистка всех данных...$(NC)"
	@read -p "Вы уверены? Это удалит ВСЕ данные! (y/N): " confirm && [ "$$confirm" = "y" ]
	@docker compose down -v --remove-orphans
	@docker system prune -f
	@echo "$(GREEN)✅ Очистка завершена$(NC)"

test: ## Запустить тесты API
	@echo "$(GREEN)🧪 Запуск тестов API...$(NC)"
	@bash scripts/simple_test.sh

dev: ## Режим разработки (rebuild + start + logs)
	@echo "$(GREEN)👨‍💻 Режим разработки...$(NC)"
	@docker compose down
	@docker compose build app
	@docker compose up -d
	@sleep 5
	@make health
	@docker compose logs -f app

prod-setup: ## Настройка для продакшена
	@echo "$(YELLOW)🏭 Настройка для продакшена...$(NC)"
	@echo "1. Создайте .env.prod файл"
	@echo "2. Установите SPRING_PROFILES_ACTIVE=prod"
	@echo "3. Обновите S3_PUBLIC_URL на ваш домен"
	@echo "4. Настройте SSL сертификаты в nginx/certs/"
	@echo "5. Отключите DEBUG_MODE и ROBOKASSA_TEST_MODE"

backup: ## Создать резервную копию данных
	@echo "$(GREEN)💾 Создание резервной копии...$(NC)"
	@mkdir -p backups/$(shell date +%Y%m%d_%H%M%S)
	@docker compose exec postgres pg_dump -U pizzanat_user pizzanat_db > backups/$(shell date +%Y%m%d_%H%M%S)/database.sql
	@echo "$(GREEN)✅ Резервная копия создана в backups/$(NC)"

# Алиасы для удобства
up: start
down: stop
ps: status 