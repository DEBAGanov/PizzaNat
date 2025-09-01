# 🔧 Диагностика проблем с логотипом СБП

## ❌ Проблема: Логотип СБП не отображается

### 🔍 Возможные причины и решения:

### 1. **Неправильный путь к файлу**
❌ **Неправильно:** `/static/images/icons/sbp-logo.svg`  
✅ **Правильно:** `/images/icons/sbp-logo.svg`

В Spring Boot статические ресурсы из папки `src/main/resources/static/` доступны напрямую без префикса `/static/`.

### 2. **Проблемы с SVG файлом**
Оригинальный SVG содержит много метаданных Inkscape, которые могут мешать отображению.

**Решение:** Используйте упрощенную версию:
- `sbp-logo-simple.svg` - очищенная версия без метаданных

### 3. **Кэширование браузера**
```bash
# Очистите кэш браузера или используйте версионирование
/images/icons/sbp-logo-simple.svg?v=1
```

### 4. **Проверка доступности файла**
Откройте в браузере напрямую:
```
http://localhost:8080/images/icons/sbp-logo-simple.svg
```

### 5. **CSS стили**
Убедитесь что CSS правила применяются:
```css
.option-title img[src*="sbp-logo"] {
    height: 40px;
    width: auto;
    max-width: 120px;
    object-fit: contain;
}
```

## 🛠️ Альтернативные решения:

### Решение 1: Inline SVG
```html
<div class="option-title">
    <svg width="120" height="40" viewBox="0 0 239 120">
        <!-- SVG содержимое здесь -->
    </svg>
</div>
```

### Решение 2: PNG fallback
```html
<div class="option-title">
    <img src="/images/icons/sbp-logo.png" alt="СБП" style="height: 40px;">
</div>
```

### Решение 3: CSS background
```css
.sbp-logo {
    background-image: url('/images/icons/sbp-logo-simple.svg');
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
    width: 120px;
    height: 40px;
}
```

## ✅ Текущее решение

Используется:
- **Путь:** `/images/icons/sbp-logo-simple.svg`
- **Размер:** 40px высота, автоматическая ширина
- **Максимальная ширина:** 120px
- **Упрощенный SVG** без метаданных

## 🔧 Отладка

1. **Проверьте консоль браузера** (F12) на ошибки 404
2. **Проверьте Network tab** - загружается ли файл
3. **Попробуйте прямой доступ** к файлу через URL
4. **Очистите кэш** браузера (Ctrl+F5)

## 📱 Файловая структура
```
src/main/resources/static/
└── images/
    └── icons/
        ├── sbp-logo.svg          ← Оригинальный (может не работать)
        ├── sbp-logo-simple.svg   ← Упрощенный (рекомендуется)
        └── sbp-logo.png          ← PNG fallback
```

---
*Руководство по диагностике: 2025-01-27*
