/**
 * PizzaNat MAX Mini App - Menu Page
 * Full product catalog for MAX messenger
 *
 * MAX WebApp SDK: https://st.max.ru/js/max-web-app.js
 * MAX global object: window.WebApp (not window.Telegram.WebApp)
 */

// Функции для отслеживания электронной коммерции в Яндекс.Метрике
function trackEcommerce(eventType, data) {
    try {
        if (typeof ym !== 'undefined') {
            console.log('📊 YM E-commerce tracking:', eventType, data);
            ym(103585127, 'reachGoal', eventType, data);

            window.dataLayer = window.dataLayer || [];
            window.dataLayer.push({
                event: eventType,
                ecommerce: data
            });
        }
    } catch (error) {
        console.error('❌ YM E-commerce tracking error:', error);
    }
}

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

function trackAddToCart(item) {
    const ecommerceData = {
        add_to_cart: {
            currency: 'RUB',
            value: item.price * item.quantity,
            items: [{
                item_id: item.productId?.toString(),
                item_name: item.name,
                category: item.category || 'Еда',
                quantity: item.quantity,
                price: item.price
            }]
        }
    };

    trackEcommerce('add_to_cart', ecommerceData);
    trackVKEcommerce('add_to_cart', {
        params: {
            product_id: item.productId?.toString()
        }
    });
}

class PizzaNatMaxMenuApp {
    constructor() {
        // MAX WebApp использует window.WebApp вместо window.Telegram.WebApp
        this.max = window.WebApp;
        this.api = window.MaxPizzaAPI;
        this.cart = { items: [], totalAmount: 0 };
        this.products = [];
        this.authToken = null;

        // Load cart from localStorage
        this.loadCartFromStorage();

        // Initialize app
        this.init();
    }

    /**
     * Склонение слова "товар" в зависимости от количества
     */
    getProductWord(count) {
        if (count === 1) {
            return 'ТОВАР';
        } else if (count >= 2 && count <= 4) {
            return 'ТОВАРА';
        } else {
            return 'ТОВАРОВ';
        }
    }

    /**
     * Инициализация приложения
     */
    async init() {
        console.log('🚀 Initializing PizzaNat MAX Menu...');

        try {
            // Настройка MAX WebApp
            this.setupMaxWebApp();

            // Авторизация
            await this.authenticate();

            // Загрузка товаров
            await this.loadProducts();

            // Настройка UI
            this.setupUI();

            // Показываем приложение
            this.showApp();

            console.log('✅ MAX Menu initialized successfully');

        } catch (error) {
            console.error('❌ MAX Menu initialization failed:', error);
            this.showError('Ошибка загрузки меню');
        }
    }

    /**
     * Настройка MAX WebApp
     */
    setupMaxWebApp() {
        if (!this.max) {
            console.warn('⚠️ MAX WebApp API not available - running in standalone mode');
            return;
        }

        console.log('📱 Setting up MAX WebApp...');
        console.log('📱 MAX WebApp available:', !!this.max);

        // Разворачиваем приложение (MAX использует Promise-based API)
        if (this.max.expand) {
            try {
                this.max.expand();
            } catch (e) {
                console.log('MAX expand not available');
            }
        }

        // Настраиваем тему
        this.applyMaxTheme();

        // Подписываемся на события MAX
        // MAX использует WebApp.on('EventName', callback)
        if (this.max.on) {
            this.max.on('WebAppThemeChanged', () => this.applyMaxTheme());
            this.max.on('WebAppReady', () => {
                console.log('✅ MAX WebApp ready');
            });
        }

        console.log('✅ MAX WebApp configured');
    }

    /**
     * Применение темы MAX
     */
    applyMaxTheme() {
        // MAX может иметь другие параметры темы
        const root = document.documentElement;

        // Базовая темная тема для MAX
        root.style.setProperty('--tg-theme-bg-color', '#1a1a1a');
        root.style.setProperty('--tg-theme-text-color', '#ffffff');
        root.style.setProperty('--tg-theme-button-color', '#ff6b35');
        root.style.setProperty('--tg-theme-button-text-color', '#ffffff');

        console.log('🎨 MAX theme applied');
    }

    /**
     * Авторизация пользователя
     */
    async authenticate() {
        // MAX WebApp использует window.WebApp.initData
        if (!this.max?.initData) {
            console.warn('⚠️ No MAX initData available - using demo mode');
            return;
        }

        console.log('🔐 Authenticating user...');
        console.log('InitData:', this.max.initData);

        try {
            const response = await this.api.authenticateWebApp(this.max.initData);
            this.authToken = response.token;

            // Устанавливаем токен в API
            this.api.setAuthToken(this.authToken);

            console.log('✅ User authenticated');
        } catch (error) {
            console.error('❌ Authentication failed:', error);
            // Продолжаем без авторизации для демонстрации
        }
    }

    /**
     * Загрузка всех товаров
     */
    async loadProducts() {
        console.log('📦 Loading products...');

        try {
            let allProducts = [];
            let page = 0;
            const pageSize = 100;
            let hasMore = true;

            while (hasMore) {
                console.log(`📄 Loading page ${page} with size ${pageSize}...`);
                const response = await this.api.getProducts(null, page, pageSize);

                if (response && response.length > 0) {
                    allProducts.push(...response);
                    page++;

                    if (response.length < pageSize) {
                        hasMore = false;
                    }
                } else {
                    hasMore = false;
                }
            }

            // Fallback к загрузке по категориям
            if (allProducts.length === 0) {
                console.log('🔄 Fallback to category-based loading...');
                const categories = await this.api.getCategories();

                for (const category of categories) {
                    const products = await this.api.getProductsByCategory(category.id);
                    if (products && products.length > 0) {
                        allProducts.push(...products);
                    }
                }
            }

            this.products = allProducts;
            this.renderProducts();

            console.log(`✅ Loaded ${this.products.length} products`);
        } catch (error) {
            console.error('❌ Failed to load products:', error);
            this.showError('Ошибка загрузки товаров');
        }
    }

    /**
     * Отображение товаров в сетке
     */
    renderProducts() {
        const grid = document.getElementById('menu-grid');
        if (!grid) return;

        grid.innerHTML = '';

        this.products.forEach(product => {
            const cartItem = this.cart.items.find(item => item.productId === product.id);
            const quantity = cartItem ? cartItem.quantity : 0;

            const productElement = document.createElement('div');
            productElement.className = 'menu-item';
            productElement.innerHTML = `
                <img src="${product.imageUrl || '/static/images/products/pizza_4_chees.png'}"
                     alt="${product.name}"
                     class="menu-item-image">
                ${quantity > 0 ? `<div class="quantity-display">${quantity}</div>` : ''}
                <div class="menu-item-info">
                    <div class="menu-item-title">${product.name}</div>
                    <div class="menu-item-price">₽${product.price}</div>
                    <div class="menu-item-actions">
                        ${quantity === 0 ?
                            `<button class="add-button" data-product-id="${product.id}">добавить</button>` :
                            `<div class="quantity-controls active">
                                <button class="quantity-btn minus" data-product-id="${product.id}">−</button>
                                <button class="quantity-btn plus" data-product-id="${product.id}">+</button>
                            </div>`
                        }
                    </div>
                </div>
            `;

            grid.appendChild(productElement);
        });

        this.updateCartUI();
    }

    /**
     * Настройка UI и обработчиков событий
     */
    setupUI() {
        // Кнопки товаров
        document.addEventListener('click', (e) => {
            if (e.target.classList.contains('cart-quantity-btn')) return;

            const productId = e.target.dataset.productId;
            if (!productId) return;

            console.log('Product button clicked:', e.target.className, 'Product ID:', productId);

            const product = this.products.find(p => p.id === parseInt(productId));
            if (!product) {
                console.error('Product not found:', productId);
                return;
            }

            if (e.target.classList.contains('add-button')) {
                this.addToCart(product, 1);
            } else if (e.target.classList.contains('plus') && !e.target.classList.contains('cart-quantity-btn')) {
                this.addToCart(product, 1);
            } else if (e.target.classList.contains('minus') && !e.target.classList.contains('cart-quantity-btn')) {
                this.removeFromCart(product.id, 1);
            }

            // Haptic feedback (если поддерживается)
            if (this.max?.haptic) {
                try {
                    this.max.haptic.impactOccurred('light');
                } catch (e) {}
            }
        });

        // Корзина
        document.getElementById('cart-button')?.addEventListener('click', () => {
            this.openCart();
        });

        document.getElementById('view-order-button')?.addEventListener('click', () => {
            this.openCart();
        });

        document.getElementById('cart-close')?.addEventListener('click', () => {
            this.closeCart();
        });

        document.getElementById('pay-button')?.addEventListener('click', () => {
            this.proceedToCheckout();
        });

        // Overlay click
        document.getElementById('cart-overlay')?.addEventListener('click', (e) => {
            if (e.target.id === 'cart-overlay') {
                this.closeCart();
            }
        });

        // Обработчики кнопок в корзине
        document.addEventListener('click', (e) => {
            if (e.target.classList.contains('cart-quantity-btn')) {
                e.preventDefault();
                e.stopPropagation();

                const productId = parseInt(e.target.dataset.productId);
                if (!productId) return;

                if (e.target.classList.contains('plus')) {
                    const existingItem = this.cart.items.find(item => item.productId === productId);
                    if (existingItem) {
                        existingItem.quantity += 1;
                        existingItem.subtotal = existingItem.quantity * existingItem.price;
                        this.updateCartTotals();
                        this.saveCartToStorage();
                        this.renderProducts();
                    }
                } else if (e.target.classList.contains('minus')) {
                    this.removeFromCart(productId, 1);
                }
            }
        });

        // Clear cart button
        document.getElementById('clear-cart-button')?.addEventListener('click', () => {
            this.clearCart();
        });

        // Retry button
        document.getElementById('retry-button')?.addEventListener('click', () => {
            location.reload();
        });
    }

    /**
     * Добавление товара в корзину
     */
    addToCart(product, quantity = 1) {
        console.log(`➕ Adding ${quantity}x ${product.name} to cart`);

        const existingItem = this.cart.items.find(item => item.productId === product.id);

        if (existingItem) {
            existingItem.quantity += quantity;
            existingItem.subtotal = existingItem.quantity * existingItem.price;
        } else {
            this.cart.items.push({
                productId: product.id,
                name: product.name,
                price: product.price,
                quantity: quantity,
                subtotal: product.price * quantity,
                imageUrl: product.imageUrl
            });
        }

        trackAddToCart({
            productId: product.id,
            name: product.name,
            price: product.price,
            quantity: quantity,
            category: 'Еда'
        });

        this.updateCartTotals();
        this.saveCartToStorage();
        this.renderProducts();

        console.log('Cart after adding:', this.cart);
    }

    /**
     * Удаление товара из корзины
     */
    removeFromCart(productId, quantity = 1) {
        console.log(`➖ Removing ${quantity}x product ${productId} from cart`);

        const itemIndex = this.cart.items.findIndex(item => item.productId === productId);
        if (itemIndex === -1) return;

        const item = this.cart.items[itemIndex];
        item.quantity -= quantity;

        if (item.quantity <= 0) {
            this.cart.items.splice(itemIndex, 1);
        } else {
            item.subtotal = item.quantity * item.price;
        }

        this.updateCartTotals();
        this.saveCartToStorage();
        this.updateCartUI();
    }

    /**
     * Очистка корзины
     */
    clearCart() {
        if (this.cart.items.length === 0) return;

        // Подтверждение очистки
        if (confirm('Очистить корзину?')) {
            this.performClearCart();
        }
    }

    /**
     * Выполнение очистки корзины
     */
    performClearCart() {
        console.log('🗑️ Clearing cart');
        this.cart.items = [];
        this.cart.totalAmount = 0;
        this.saveCartToStorage();
        this.updateCartUI();
        this.closeCart();
    }

    /**
     * Обновление общих сумм корзины
     */
    updateCartTotals() {
        this.cart.totalAmount = this.cart.items.reduce((total, item) => total + item.subtotal, 0);
        this.updateCartUI();
    }

    /**
     * Обновление UI корзины
     */
    updateCartUI() {
        const cartCount = this.cart.items.reduce((total, item) => total + item.quantity, 0);
        const totalAmount = this.cart.totalAmount;

        const cartCountElements = document.querySelectorAll('#cart-count');
        cartCountElements.forEach(el => el.textContent = cartCount);

        const cartTotalElements = document.querySelectorAll('#cart-total');
        cartTotalElements.forEach(el => el.textContent = `₽${totalAmount}`);

        const bottomCountElement = document.getElementById('bottom-count');
        const bottomTotalElement = document.getElementById('bottom-total');
        if (bottomCountElement) bottomCountElement.textContent = cartCount;
        if (bottomTotalElement) bottomTotalElement.textContent = `₽${totalAmount}`;

        const viewOrderButton = document.getElementById('view-order-button');
        if (viewOrderButton && cartCount > 0) {
            viewOrderButton.innerHTML = `<span id="bottom-count">${cartCount}</span> ${this.getProductWord(cartCount)} НА <span id="bottom-total">₽${totalAmount}</span>`;
        }

        const bottomBar = document.getElementById('bottom-bar');
        if (bottomBar) {
            bottomBar.style.display = cartCount > 0 ? 'block' : 'none';
        }

        this.renderCartItems();
    }

    /**
     * Отображение товаров в корзине
     */
    renderCartItems() {
        const cartContent = document.getElementById('cart-content');
        if (!cartContent) return;

        cartContent.innerHTML = '';

        this.cart.items.forEach(item => {
            const itemElement = document.createElement('div');
            itemElement.className = 'cart-item';
            itemElement.innerHTML = `
                <img src="${item.imageUrl || '/static/images/products/pizza_4_chees.png'}"
                     alt="${item.name}"
                     class="cart-item-image">
                <div class="cart-item-info">
                    <div class="cart-item-title">${item.name}</div>
                </div>
                <div class="cart-item-controls">
                    <button class="cart-quantity-btn minus" data-product-id="${item.productId}">−</button>
                    <span class="cart-item-quantity">${item.quantity}</span>
                    <button class="cart-quantity-btn plus" data-product-id="${item.productId}">+</button>
                </div>
                <div class="cart-item-price">₽${item.subtotal}</div>
            `;
            cartContent.appendChild(itemElement);
        });
    }

    /**
     * Открытие корзины
     */
    openCart() {
        if (this.cart.items.length === 0) {
            this.showError('Корзина пуста');
            return;
        }

        document.getElementById('cart-overlay').style.display = 'flex';
        document.body.style.overflow = 'hidden';
    }

    /**
     * Закрытие корзины
     */
    closeCart() {
        document.getElementById('cart-overlay').style.display = 'none';
        document.body.style.overflow = 'auto';
    }

    /**
     * Оформление заказа
     */
    async proceedToCheckout() {
        if (this.cart.items.length === 0) {
            this.showError('Корзина пуста');
            return;
        }

        // Сохраняем корзину в localStorage
        this.saveCartToStorage();

        // Переходим на страницу оформления заказа
        window.location.href = 'checkout.html';
    }

    /**
     * Сохранение корзины в localStorage
     */
    saveCartToStorage() {
        try {
            localStorage.setItem('pizzanat_max_cart', JSON.stringify(this.cart));
        } catch (error) {
            console.warn('Failed to save cart to localStorage:', error);
        }
    }

    /**
     * Загрузка корзины из localStorage
     */
    loadCartFromStorage() {
        try {
            const saved = localStorage.getItem('pizzanat_max_cart');
            if (saved) {
                this.cart = JSON.parse(saved);
            }
        } catch (error) {
            console.warn('Failed to load cart from localStorage:', error);
            this.cart = { items: [], totalAmount: 0 };
        }
    }

    /**
     * Показать приложение
     */
    showApp() {
        document.getElementById('loading-screen').style.display = 'none';
        document.getElementById('app').style.display = 'block';
    }

    /**
     * Показать ошибку
     */
    showError(message) {
        document.getElementById('error-message').textContent = message;
        document.getElementById('loading-screen').style.display = 'none';
        document.getElementById('app').style.display = 'none';
        document.getElementById('error-screen').style.display = 'flex';
    }
}

// Инициализация приложения
document.addEventListener('DOMContentLoaded', () => {
    new PizzaNatMaxMenuApp();
});
