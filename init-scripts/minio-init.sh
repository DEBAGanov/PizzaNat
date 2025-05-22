#!/bin/sh
set -e

# Параметры подключения к MinIO
MINIO_HOST="${MINIO_HOST:-minio:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-accesskey}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-secretkey}"
BUCKET_NAME="${BUCKET_NAME:-pizzanat}"
IMAGES_DIR="/images"

echo "Инициализация MinIO: $MINIO_HOST, bucket: $BUCKET_NAME"
echo "Проверка содержимого папки с изображениями:"
ls -la $IMAGES_DIR

echo "Ожидание запуска MinIO..."

# Ждем, пока MinIO запустится - simpler check without curl
max_retries=30
retry_count=0
while [ $retry_count -lt $max_retries ]; do
  # Пытаемся сразу настроить alias, что проверит доступность
  if mc alias set myminio http://$MINIO_HOST $MINIO_ACCESS_KEY $MINIO_SECRET_KEY > /dev/null 2>&1; then
    echo "MinIO готов!"
    break
  fi
  
  retry_count=$((retry_count+1))
  if [ $retry_count -ge $max_retries ]; then
    echo "Превышено максимальное количество попыток подключения к MinIO"
    exit 1
  fi
  echo "Ожидание MinIO, попытка $retry_count из $max_retries"
  sleep 3
done

echo "Настройка MinIO..."

# Настройка клиента MinIO (повторяем для уверенности)
mc alias set myminio http://$MINIO_HOST $MINIO_ACCESS_KEY $MINIO_SECRET_KEY

# Создание бакета и настройка прав
mc mb --ignore-existing myminio/$BUCKET_NAME
mc anonymous set download myminio/$BUCKET_NAME

# Создание структуры директорий
mc mb --ignore-existing myminio/$BUCKET_NAME/products
mc mb --ignore-existing myminio/$BUCKET_NAME/categories

# Загрузка изображений продуктов из корневой директории
echo "Загрузка изображений из корневой директории $IMAGES_DIR..."
for file in $IMAGES_DIR/pizza_*.png; do
  # Проверка существования файла
  if [ -e "$file" ]; then
    filename=$(basename "$file")
    objectname="products/${filename}"
    
    echo "Загрузка файла: $filename -> $objectname"
    mc cp "$file" myminio/$BUCKET_NAME/$objectname
  fi
done

# Загрузка изображений продуктов из директории products
echo "Загрузка изображений из директории products..."
for file in $IMAGES_DIR/products/*.png; do
  # Проверка существования файла
  if [ -e "$file" ]; then
    filename=$(basename "$file")
    objectname="products/${filename}"
    
    echo "Загрузка файла из products: $filename -> $objectname"
    mc cp "$file" myminio/$BUCKET_NAME/$objectname
  fi
done

# Загрузка изображений категорий
echo "Загрузка изображений категорий..."
for file in $IMAGES_DIR/categories/*.png; do
  # Проверка существования файла
  if [ -e "$file" ]; then
    filename=$(basename "$file")
    objectname="categories/${filename}"
    
    echo "Загрузка файла категории: $filename -> $objectname"
    mc cp "$file" myminio/$BUCKET_NAME/$objectname
  fi
done

echo "Проверка загруженных объектов:"
mc ls myminio/$BUCKET_NAME/products/
mc ls myminio/$BUCKET_NAME/categories/

echo "✅ Инициализация MinIO успешно завершена!"
