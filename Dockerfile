FROM openjdk:21-slim as builder

WORKDIR /app

COPY . .

# Установка необходимых инструментов
RUN apt-get update && apt-get install -y curl unzip

# Сборка приложения
RUN chmod +x ./gradlew && ./gradlew build -x test

FROM openjdk:21-slim

WORKDIR /app

# Копирование JAR-файла из этапа сборки
COPY --from=builder /app/build/libs/*.jar app.jar

# Скрипт запуска приложения
ENTRYPOINT ["java", "-jar", "app.jar"]
