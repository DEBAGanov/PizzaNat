#!/bin/bash

# Скрипт настройки Grafana дашборда для мониторинга ЮKassa
# Автор: AI Assistant
# Дата: 26.01.2025

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Настройки по умолчанию
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

# Функции логирования
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_header() {
    echo -e "\n${PURPLE}=== $1 ===${NC}"
}

# Функция проверки зависимостей
check_dependencies() {
    log_header "ПРОВЕРКА ЗАВИСИМОСТЕЙ"
    
    if ! command -v curl &> /dev/null; then
        log_error "curl не установлен"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        log_error "jq не установлен"
        exit 1
    fi
    
    log_success "Все зависимости установлены"
}

# Функция проверки доступности Grafana
check_grafana_connection() {
    log_header "ПРОВЕРКА ПОДКЛЮЧЕНИЯ К GRAFANA"
    
    log_info "Проверяю доступность Grafana: $GRAFANA_URL"
    
    if curl -s -f "$GRAFANA_URL/api/health" > /dev/null; then
        log_success "Grafana доступна"
    else
        log_error "Grafana недоступна по адресу: $GRAFANA_URL"
        log_info "Убедитесь, что Grafana запущена и доступна"
        exit 1
    fi
}

# Функция создания источника данных Prometheus
create_prometheus_datasource() {
    log_header "СОЗДАНИЕ ИСТОЧНИКА ДАННЫХ PROMETHEUS"
    
    local datasource_config=$(cat <<EOF
{
  "name": "PizzaNat-Prometheus",
  "type": "prometheus",
  "url": "$PROMETHEUS_URL",
  "access": "proxy",
  "isDefault": true,
  "basicAuth": false,
  "jsonData": {
    "httpMethod": "POST",
    "prometheusType": "Prometheus",
    "prometheusVersion": "2.40.0"
  }
}
EOF
)

    log_info "Создаю источник данных Prometheus..."
    
    local response=$(curl -s -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -u "$GRAFANA_USER:$GRAFANA_PASSWORD" \
        -d "$datasource_config" \
        "$GRAFANA_URL/api/datasources")
    
    local http_code="${response: -3}"
    local body="${response%???}"
    
    if [[ "$http_code" == "200" || "$http_code" == "409" ]]; then
        log_success "Источник данных Prometheus настроен"
    else
        log_error "Ошибка создания источника данных: HTTP $http_code"
        echo "$body"
        exit 1
    fi
}

# Функция создания дашборда ЮKassa
create_yookassa_dashboard() {
    log_header "СОЗДАНИЕ ДАШБОРДА YOOKASSA"
    
    local dashboard_config=$(cat <<'EOF'
{
  "dashboard": {
    "id": null,
    "title": "ЮKassa Мониторинг",
    "tags": ["yookassa", "payments", "pizzanat"],
    "timezone": "Europe/Moscow",
    "refresh": "30s",
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "panels": [
      {
        "id": 1,
        "title": "Общая статистика платежей",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(yookassa_payments_total)",
            "legendFormat": "Всего платежей"
          },
          {
            "expr": "sum(yookassa_payments_success)",
            "legendFormat": "Успешных"
          },
          {
            "expr": "sum(yookassa_payments_failure)",
            "legendFormat": "Неудачных"
          }
        ],
        "gridPos": {"h": 8, "w": 8, "x": 0, "y": 0},
        "fieldConfig": {
          "defaults": {
            "color": {"mode": "palette-classic"},
            "custom": {
              "displayMode": "basic"
            },
            "unit": "short"
          }
        }
      },
      {
        "id": 2,
        "title": "Конверсия платежей (%)",
        "type": "stat",
        "targets": [
          {
            "expr": "(sum(yookassa_payments_success) / sum(yookassa_payments_total)) * 100",
            "legendFormat": "Конверсия"
          }
        ],
        "gridPos": {"h": 8, "w": 8, "x": 8, "y": 0},
        "fieldConfig": {
          "defaults": {
            "color": {"mode": "thresholds"},
            "thresholds": {
              "steps": [
                {"color": "red", "value": 0},
                {"color": "yellow", "value": 70},
                {"color": "green", "value": 85}
              ]
            },
            "unit": "percent"
          }
        }
      },
      {
        "id": 3,
        "title": "Средний чек (₽)",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(yookassa_payments_amount_total) / sum(yookassa_payments_success)",
            "legendFormat": "Средний чек"
          }
        ],
        "gridPos": {"h": 8, "w": 8, "x": 16, "y": 0},
        "fieldConfig": {
          "defaults": {
            "color": {"mode": "palette-classic"},
            "unit": "currencyRUB"
          }
        }
      },
      {
        "id": 4,
        "title": "Платежи по времени",
        "type": "timeseries",
        "targets": [
          {
            "expr": "rate(yookassa_payments_total[5m]) * 60",
            "legendFormat": "Всего в минуту"
          },
          {
            "expr": "rate(yookassa_payments_success[5m]) * 60",
            "legendFormat": "Успешных в минуту"
          },
          {
            "expr": "rate(yookassa_payments_failure[5m]) * 60",
            "legendFormat": "Неудачных в минуту"
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 8},
        "fieldConfig": {
          "defaults": {
            "color": {"mode": "palette-classic"},
            "custom": {
              "drawStyle": "line",
              "lineInterpolation": "linear",
              "fillOpacity": 10
            },
            "unit": "short"
          }
        }
      },
      {
        "id": 5,
        "title": "Время создания платежа",
        "type": "timeseries",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(yookassa_payments_creation_time_bucket[5m]))",
            "legendFormat": "95-й процентиль"
          },
          {
            "expr": "histogram_quantile(0.5, rate(yookassa_payments_creation_time_bucket[5m]))",
            "legendFormat": "Медиана"
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 8},
        "fieldConfig": {
          "defaults": {
            "color": {"mode": "palette-classic"},
            "unit": "ms"
          }
        }
      },
      {
        "id": 6,
        "title": "Платежи по методам",
        "type": "piechart",
        "targets": [
          {
            "expr": "sum by (method) (yookassa_payments_by_method)",
            "legendFormat": "{{method}}"
          }
        ],
        "gridPos": {"h": 8, "w": 8, "x": 0, "y": 16},
        "fieldConfig": {
          "defaults": {
            "color": {"mode": "palette-classic"},
            "custom": {
              "hideFrom": {
                "tooltip": false,
                "vis": false,
                "legend": false
              }
            }
          }
        }
      },
      {
        "id": 7,
        "title": "Статусы платежей",
        "type": "piechart",
        "targets": [
          {
            "expr": "sum by (status) (yookassa_payments_by_status)",
            "legendFormat": "{{status}}"
          }
        ],
        "gridPos": {"h": 8, "w": 8, "x": 8, "y": 16},
        "fieldConfig": {
          "defaults": {
            "color": {"mode": "palette-classic"}
          }
        }
      },
      {
        "id": 8,
        "title": "Webhook обработка",
        "type": "timeseries",
        "targets": [
          {
            "expr": "rate(yookassa_webhook_received[5m]) * 60",
            "legendFormat": "Получено в минуту"
          },
          {
            "expr": "rate(yookassa_webhook_processed[5m]) * 60",
            "legendFormat": "Обработано в минуту"
          },
          {
            "expr": "rate(yookassa_webhook_failed[5m]) * 60",
            "legendFormat": "Ошибок в минуту"
          }
        ],
        "gridPos": {"h": 8, "w": 8, "x": 16, "y": 16}
      }
    ]
  },
  "overwrite": true
}
EOF
)

    log_info "Создаю дашборд ЮKassa..."
    
    local response=$(curl -s -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -u "$GRAFANA_USER:$GRAFANA_PASSWORD" \
        -d "$dashboard_config" \
        "$GRAFANA_URL/api/dashboards/db")
    
    local http_code="${response: -3}"
    local body="${response%???}"
    
    if [[ "$http_code" == "200" ]]; then
        local dashboard_url=$(echo "$body" | jq -r '.url')
        log_success "Дашборд ЮKassa создан: $GRAFANA_URL$dashboard_url"
    else
        log_error "Ошибка создания дашборда: HTTP $http_code"
        echo "$body"
        exit 1
    fi
}

# Функция создания алертов
create_alerts() {
    log_header "СОЗДАНИЕ АЛЕРТОВ"
    
    # Алерт для низкой конверсии
    local low_conversion_alert=$(cat <<'EOF'
{
  "alert": {
    "id": null,
    "title": "ЮKassa: Низкая конверсия",
    "message": "Конверсия платежей ЮKassa упала ниже 70%",
    "frequency": "60s",
    "conditions": [
      {
        "query": {
          "queryType": "",
          "refId": "A",
          "model": {
            "expr": "(sum(yookassa_payments_success) / sum(yookassa_payments_total)) * 100",
            "interval": "",
            "refId": "A"
          }
        },
        "reducer": {
          "type": "last",
          "params": []
        },
        "evaluator": {
          "params": [70],
          "type": "lt"
        }
      }
    ],
    "executionErrorState": "alerting",
    "noDataState": "no_data",
    "for": "5m"
  }
}
EOF
)

    # Алерт для высокого уровня ошибок
    local high_failure_alert=$(cat <<'EOF'
{
  "alert": {
    "id": null,
    "title": "ЮKassa: Высокий уровень ошибок",
    "message": "Уровень ошибок платежей ЮKassa превысил 10%",
    "frequency": "60s",
    "conditions": [
      {
        "query": {
          "queryType": "",
          "refId": "A",
          "model": {
            "expr": "(sum(yookassa_payments_failure) / sum(yookassa_payments_total)) * 100",
            "interval": "",
            "refId": "A"
          }
        },
        "reducer": {
          "type": "last",
          "params": []
        },
        "evaluator": {
          "params": [10],
          "type": "gt"
        }
      }
    ],
    "executionErrorState": "alerting",
    "noDataState": "no_data",
    "for": "5m"
  }
}
EOF
)

    log_info "Создание алертов в Grafana..."
    log_warning "Алерты требуют настройки каналов уведомлений в Grafana"
}

# Функция создания конфигурации Prometheus
create_prometheus_config() {
    log_header "СОЗДАНИЕ КОНФИГУРАЦИИ PROMETHEUS"
    
    local prometheus_config=$(cat <<EOF
# Конфигурация Prometheus для мониторинга PizzaNat ЮKassa
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "yookassa_alerts.yml"

scrape_configs:
  - job_name: 'pizzanat-yookassa'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    scrape_timeout: 10s
    honor_labels: true
    params:
      format: ['prometheus']

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
EOF
)

    echo "$prometheus_config" > prometheus-yookassa.yml
    log_success "Конфигурация Prometheus сохранена: prometheus-yookassa.yml"
    
    # Создание правил алертов
    local alert_rules=$(cat <<EOF
groups:
  - name: yookassa_alerts
    rules:
      - alert: YookassaLowConversion
        expr: (sum(yookassa_payments_success) / sum(yookassa_payments_total)) * 100 < 70
        for: 5m
        labels:
          severity: warning
          service: yookassa
        annotations:
          summary: "Низкая конверсия платежей ЮKassa"
          description: "Конверсия платежей составляет {{ \$value }}% (< 70%)"
      
      - alert: YookassaHighFailureRate
        expr: (sum(yookassa_payments_failure) / sum(yookassa_payments_total)) * 100 > 10
        for: 5m
        labels:
          severity: critical
          service: yookassa
        annotations:
          summary: "Высокий уровень ошибок ЮKassa"
          description: "Уровень ошибок составляет {{ \$value }}% (> 10%)"
      
      - alert: YookassaSlowPaymentCreation
        expr: histogram_quantile(0.95, rate(yookassa_payments_creation_time_bucket[5m])) > 5000
        for: 2m
        labels:
          severity: warning
          service: yookassa
        annotations:
          summary: "Медленное создание платежей ЮKassa"
          description: "95-й процентиль времени создания: {{ \$value }}ms (> 5000ms)"
EOF
)

    echo "$alert_rules" > yookassa_alerts.yml
    log_success "Правила алертов сохранены: yookassa_alerts.yml"
}

# Функция создания docker-compose для мониторинга
create_monitoring_compose() {
    log_header "СОЗДАНИЕ DOCKER-COMPOSE ДЛЯ МОНИТОРИНГА"
    
    local compose_config=$(cat <<EOF
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: pizzanat-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus-yookassa.yml:/etc/prometheus/prometheus.yml
      - ./yookassa_alerts.yml:/etc/prometheus/yookassa_alerts.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--storage.tsdb.retention.time=200h'
      - '--web.enable-lifecycle'
    restart: unless-stopped
    networks:
      - monitoring

  grafana:
    image: grafana/grafana:latest
    container_name: pizzanat-grafana
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_USER=$GRAFANA_USER
      - GF_SECURITY_ADMIN_PASSWORD=$GRAFANA_PASSWORD
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_SERVER_DOMAIN=localhost
      - GF_SMTP_ENABLED=false
    restart: unless-stopped
    networks:
      - monitoring

  alertmanager:
    image: prom/alertmanager:latest
    container_name: pizzanat-alertmanager
    ports:
      - "9093:9093"
    volumes:
      - ./alertmanager.yml:/etc/alertmanager/alertmanager.yml
      - alertmanager_data:/alertmanager
    restart: unless-stopped
    networks:
      - monitoring

volumes:
  prometheus_data:
  grafana_data:
  alertmanager_data:

networks:
  monitoring:
    driver: bridge
EOF
)

    echo "$compose_config" > docker-compose.monitoring.yml
    log_success "Docker Compose конфигурация сохранена: docker-compose.monitoring.yml"
}

# Основная функция
main() {
    log_header "🔧 НАСТРОЙКА GRAFANA ДАШБОРДА ДЛЯ YOOKASSA"
    echo "Дата: $(date '+%d.%m.%Y %H:%M:%S')"
    echo ""
    
    # Проверка параметров
    if [[ "$1" == "--help" || "$1" == "-h" ]]; then
        echo "Использование: $0 [опции]"
        echo ""
        echo "Переменные окружения:"
        echo "  GRAFANA_URL      - URL Grafana (по умолчанию: http://localhost:3000)"
        echo "  GRAFANA_USER     - Пользователь Grafana (по умолчанию: admin)"
        echo "  GRAFANA_PASSWORD - Пароль Grafana (по умолчанию: admin)"
        echo "  PROMETHEUS_URL   - URL Prometheus (по умолчанию: http://localhost:9090)"
        echo ""
        echo "Примеры:"
        echo "  $0                                    # Настройка с параметрами по умолчанию"
        echo "  GRAFANA_URL=http://grafana:3000 $0    # Настройка с кастомным URL"
        exit 0
    fi
    
    # Выполнение настройки
    check_dependencies
    create_prometheus_config
    create_monitoring_compose
    
    if [[ "$1" != "--config-only" ]]; then
        check_grafana_connection
        create_prometheus_datasource
        create_yookassa_dashboard
        create_alerts
    fi
    
    # Итоговая информация
    log_header "📊 НАСТРОЙКА ЗАВЕРШЕНА"
    
    echo -e "${GREEN}✅ Созданы файлы конфигурации:${NC}"
    echo "  - prometheus-yookassa.yml     # Конфигурация Prometheus"
    echo "  - yookassa_alerts.yml         # Правила алертов"
    echo "  - docker-compose.monitoring.yml # Docker Compose для мониторинга"
    echo ""
    
    if [[ "$1" != "--config-only" ]]; then
        echo -e "${GREEN}✅ Настроена Grafana:${NC}"
        echo "  - Источник данных Prometheus подключен"
        echo "  - Дашборд ЮKassa создан"
        echo "  - URL: $GRAFANA_URL"
        echo ""
    fi
    
    echo -e "${CYAN}📋 Следующие шаги:${NC}"
    echo "1. Запустите мониторинг: docker-compose -f docker-compose.monitoring.yml up -d"
    echo "2. Откройте Grafana: $GRAFANA_URL"
    echo "3. Войдите с учетными данными: $GRAFANA_USER / $GRAFANA_PASSWORD"
    echo "4. Проверьте дашборд 'ЮKassa Мониторинг'"
    echo "5. Настройте каналы уведомлений для алертов"
    echo ""
    
    echo -e "${YELLOW}⚠️ Важно:${NC}"
    echo "- Убедитесь, что PizzaNat приложение доступно на localhost:8080"
    echo "- Prometheus должен иметь доступ к /actuator/prometheus endpoint"
    echo "- Для продакшена измените пароли и настройте SSL"
}

# Запуск
main "$@" 