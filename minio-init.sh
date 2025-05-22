#!/bin/bash
set -e

# Константы
MINIO_HOST="minio:9000"
MINIO_ACCESS_KEY="accesskey"
MINIO_SECRET_KEY="secretkey"
BUCKET_NAME="pizzanat"
IMAGES_DIR="/images"

# Ждем, пока MinIO будет доступен
echo "Ожидание запуска MinIO..."
until curl -s http://${MINIO_HOST}/minio/health/live > /dev/null; do
  echo "MinIO еще не готов - ожидание..."
  sleep 1
done

# Настраиваем клиент MinIO
mc alias set myminio http://${MINIO_HOST} ${MINIO_ACCESS_KEY} ${MINIO_SECRET_KEY}

# Создаем бакет, если его нет
mc mb --ignore-existing myminio/${BUCKET_NAME}

# Устанавливаем публичный доступ на чтение
mc anonymous set download myminio/${BUCKET_NAME}

# Создаем структуру директорий
mc mb --ignore-existing myminio/${BUCKET_NAME}/products
mc mb --ignore-existing myminio/${BUCKET_NAME}/categories

# Конвертация и загрузка изображений
echo "Загрузка изображений в MinIO..."
for file in ${IMAGES_DIR}/*.png; do
  filename=$(basename $file)
  objectname="products/${filename}"

  # Оптимизация изображения, если установлен ImageMagick
  if command -v convert > /dev/null; then
    echo "Оптимизация изображения: $filename"
    # Создаем временный файл
    optimized_file="/tmp/${filename}"
    # Оптимизируем изображение
    convert "$file" -resize 600x600 -quality 85 "$optimized_file"
    # Загружаем оптимизированное изображение
    mc cp --attr="content-type=image/png" "$optimized_file" myminio/${BUCKET_NAME}/$objectname
    # Удаляем временный файл
    rm "$optimized_file"
  else
    # Загружаем оригинальное изображение без оптимизации
    mc cp --attr="content-type=image/png" "$file" myminio/${BUCKET_NAME}/$objectname
  fi

  echo "Загружено: $filename -> $objectname"
done

echo "Инициализация MinIO завершена успешно"
