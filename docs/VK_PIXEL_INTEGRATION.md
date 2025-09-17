# Интеграция VK пикселя (Top.Mail.Ru) с электронной коммерцией

## Обзор

В проект PizzaNat успешно интегрирован VK пиксель (Top.Mail.Ru счетчик) с поддержкой событий электронной коммерции. Интеграция выполнена параллельно с существующей Яндекс.Метрикой, что позволяет отслеживать пользователей и настраивать ретаргетинг в VK Рекламе.

## ID пикселя VK

**Pixel ID:** `3695469`

## Интегрированные файлы

### HTML файлы
- `src/main/resources/static/miniapp/index.html`
- `src/main/resources/static/miniapp/menu.html` 
- `src/main/resources/static/miniapp/checkout.html`

### JavaScript файлы
- `src/main/resources/static/miniapp/app.js`
- `src/main/resources/static/miniapp/menu-app.js`
- `src/main/resources/static/miniapp/checkout-app.js`

## Счетчик VK пикселя

В каждый HTML файл добавлен код счетчика:

```html
<!-- VK Pixel (Top.Mail.Ru counter) -->
<script type="text/javascript">
    var _tmr = window._tmr || (window._tmr = []);
    _tmr.push({id: "3695469", type: "pageView", start: (new Date()).getTime()});
    (function (d, w, id) {
      if (d.getElementById(id)) return;
      var ts = d.createElement("script"); ts.type = "text/javascript"; ts.async = true; ts.id = id;
      ts.src = "https://top-fwz1.mail.ru/js/code.js";
      var f = function () {var s = d.getElementsByTagName("script")[0]; s.parentNode.insertBefore(ts, s);};
      if (w.opera == "[object Opera]") { d.addEventListener("DOMContentLoaded", f, false); } else { f(); }
    })(document, window, "tmr-code");
</script>
<noscript><div><img src="https://top-fwz1.mail.ru/counter?id=3695469;js=na" style="position:absolute;left:-9999px;" alt="Top.Mail.Ru" /></div></noscript>
<!-- /VK Pixel (Top.Mail.Ru counter) -->
```

## Функции отслеживания E-commerce

### Основная функция трекинга VK

```javascript
// Функции для отслеживания событий VK пикселя (Top.Mail.Ru)
function trackVKEcommerce(goal, data) {
    try {
        if (typeof _tmr !== 'undefined' && Array.isArray(_tmr)) {
            console.log('📊 VK E-commerce tracking:', goal, data);
            _tmr.push({
                type: "reachGoal",
                id: "3695469",
                goal: goal,
                value: data.value || undefined,
                params: data.params || {}
            });
        }
    } catch (error) {
        console.error('❌ VK E-commerce tracking error:', error);
    }
}
```

### Отслеживаемые события

#### 1. Просмотр товара (view_item)
```javascript
function trackViewItem(item) {
    // Яндекс Метрика
    trackEcommerce('view_item', ecommerceData);
    
    // VK пиксель
    trackVKEcommerce('view_item', {
        params: {
            product_id: item.productId?.toString()
        }
    });
}
```

#### 2. Добавление в корзину (add_to_cart)
```javascript
function trackAddToCart(item) {
    // Яндекс Метрика
    trackEcommerce('add_to_cart', ecommerceData);
    
    // VK пиксель
    trackVKEcommerce('add_to_cart', {
        params: {
            product_id: item.productId?.toString()
        }
    });
}
```

#### 3. Начало оформления заказа (initiate_checkout)
```javascript
function trackBeginCheckout(items, totalAmount) {
    // Яндекс Метрика
    trackEcommerce('begin_checkout', ecommerceData);
    
    // VK пиксель
    const productIds = items.map(item => item.productId?.toString());
    trackVKEcommerce('initiate_checkout', {
        value: totalAmount,
        params: {
            product_id: productIds.length === 1 ? productIds[0] : productIds
        }
    });
}
```

#### 4. Покупка (purchase)
```javascript
function trackPurchase(orderData, items) {
    // Яндекс Метрика
    trackEcommerce('purchase', ecommerceData);
    
    // VK пиксель
    const productIds = items.map(item => item.productId?.toString());
    trackVKEcommerce('purchase', {
        value: orderData.totalAmount,
        params: {
            product_id: productIds.length === 1 ? productIds[0] : productIds
        }
    });
}
```

## Места вызова событий

### 1. Добавление в корзину
- Файл: `app.js`, `menu-app.js`
- Метод: `addToCart()`
- Строка: ~517 в app.js, ~502 в menu-app.js

### 2. Начало оформления заказа  
- Файл: `checkout-app.js`
- Метод: `init()`
- Строка: ~184

### 3. Покупка
- Файл: `checkout-app.js`
- Метод: `submitOrder()`
- Строки: ~1666, ~1684

- Файл: `app.js`
- Метод: `proceedToCheckout()`
- Строка: ~787

- Файл: `menu-app.js`
- Метод: `proceedToCheckout()`
- Строка: ~768

## Структура данных событий VK

### Формат отправки в VK пиксель
```javascript
_tmr.push({
    type: "reachGoal",
    id: "3695469",
    goal: "event_name",
    value: 1200,  // стоимость заказа/товара (опционально)
    params: {
        product_id: "123"  // или ["123", "456"] для множественных товаров
    }
});
```

## Настройка в VK Рекламе

1. В VK Рекламе перейдите в раздел "Центр коммерции"
2. Создайте события соответствующие отправляемым целям:
   - `view_item` - просмотр товара
   - `add_to_cart` - добавление в корзину  
   - `initiate_checkout` - начало оформления заказа
   - `purchase` - покупка

3. Настройте динамический ретаргетинг на основе этих событий

## Логирование и отладка

Все события VK пикселя логируются в консоль браузера:
```
📊 VK E-commerce tracking: add_to_cart {params: {product_id: "123"}}
📊 VK E-commerce tracking: initiate_checkout {value: 1200, params: {product_id: ["123", "456"]}}
📊 VK E-commerce tracking: purchase {value: 1200, params: {product_id: ["123", "456"]}}
```

## Совместимость

Интеграция VK пикселя полностью совместима с существующей Яндекс.Метрикой:
- Оба счетчика работают параллельно
- Не влияют друг на друга
- Отслеживают одни и те же события
- Используют одинаковую структуру данных

## Проверка работоспособности

1. Откройте Developer Tools браузера
2. Перейдите на вкладку Network
3. Выполните действия в приложении (добавление в корзину, покупка)
4. Проверьте запросы к `top-fwz1.mail.ru`
5. Проверьте логи в консоли с префиксом "📊 VK E-commerce tracking"

## Статус интеграции

✅ VK пиксель установлен во всех HTML файлах  
✅ Функции трекинга E-commerce добавлены во всех JS файлах  
✅ События отслеживания интегрированы в существующий код  
✅ Совместимость с Яндекс.Метрикой обеспечена  
✅ Логирование и отладка настроены  

## Дата интеграции

Интеграция выполнена: **17 сентября 2025**

---
*Документ создан автоматически в процессе интеграции VK пикселя в проект PizzaNat*
