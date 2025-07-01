#!/bin/sh
set -e

# Ожидание готовности базы данных и других зависимостей
if [ "$STARTUP_SLEEP" ]; then
  echo "Ожидание $STARTUP_SLEEP секунд перед запуском..."
  sleep "$STARTUP_SLEEP"
  echo "Ожидание завершено."
fi

# Запуск приложения
echo "Запуск приложения..."
exec java -jar app.jar "$@"
