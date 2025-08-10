/**
 * PizzaNat Mini App - Menu Page
 * Full product catalog like @DurgerKingBot
 */

class PizzaNatMenuApp {
    constructor() {
        this.tg = window.Telegram?.WebApp;
        this.api = window.PizzaAPI;
        this.cart = { items: [], totalAmount: 0 };
        this.products = [];
        this.authToken = null;
        
        // Load cart from localStorage
        this.loadCartFromStorage();
        
        // Initialize app
        this.init();
    }

    /**
     * Инициализация приложения
     */
    async init() {
        console.log('🚀 Initializing PizzaNat Menu...');
        
        try {
            // Настройка Telegram WebApp
            this.setupTelegramWebApp();
            
            // Авторизация
            await this.authenticate();
            
            // Загрузка товаров
            await this.loadProducts();
            
            // Настройка UI
            this.setupUI();
            
            // Показываем приложение
            this.showApp();
            
            console.log('✅ Menu initialized successfully');
            
        } catch (error) {
            console.error('❌ Menu initialization failed:', error);
            this.showError('Ошибка загрузки меню');
        }
    }

    /**
     * Настройка Telegram WebApp
     */
    setupTelegramWebApp() {
        if (!this.tg) {
            console.warn('⚠️ Telegram WebApp API not available');
            return;
        }

        console.log('📱 Setting up Telegram WebApp...');

        // Разворачиваем приложение
        this.tg.expand();
        
        // Настраиваем тему
        this.applyTelegramTheme();
        
        // Настраиваем back button
        if (this.tg.BackButton) {
            this.tg.BackButton.show();
            this.tg.BackButton.onClick(() => {
                this.tg.close();
            });
        }
        
        // Подписываемся на события
        this.tg.onEvent('themeChanged', () => this.applyTelegramTheme());
        this.tg.onEvent('contactRequested', (data) => this.handleContactReceived(data));
        
        console.log('✅ Telegram WebApp configured');
    }

    /**
     * Применение темы Telegram
     */
    applyTelegramTheme() {
        if (!this.tg?.themeParams) return;

        const themeParams = this.tg.themeParams;
        const root = document.documentElement;

        // Применяем цвета темы
        if (themeParams.bg_color) {
            root.style.setProperty('--tg-theme-bg-color', themeParams.bg_color);
        }
        if (themeParams.text_color) {
            root.style.setProperty('--tg-theme-text-color', themeParams.text_color);
        }
        if (themeParams.button_color) {
            root.style.setProperty('--tg-theme-button-color', themeParams.button_color);
        }
        if (themeParams.button_text_color) {
            root.style.setProperty('--tg-theme-button-text-color', themeParams.button_text_color);
        }
    }

    /**
     * Авторизация пользователя
     */
    async authenticate() {
        if (!this.tg?.initData) {
            console.warn('⚠️ No Telegram initData available - using demo mode');
            return;
        }

        console.log('🔐 Authenticating user...');
        console.log('InitData:', this.tg.initData);

        try {
            const response = await this.api.authenticateWebApp(this.tg.initData);
            this.authToken = response.token;
            
            // Устанавливаем токен в API
            this.api.setAuthToken(this.authToken);
            
            console.log('✅ User authenticated');
        } catch (error) {
            console.error('❌ Authentication failed:', error);
            console.error('Error details:', error);
            // Продолжаем без авторизации для демонстрации
        }
    }

    /**
     * Загрузка всех товаров
     */
    async loadProducts() {
        console.log('📦 Loading products...');

        try {
            // Загружаем все категории и их товары
            const categories = await this.api.getCategories();
            const allProducts = [];

            for (const category of categories) {
                const products = await this.api.getProductsByCategory(category.id);
                allProducts.push(...products);
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
                            `<button class="add-button" data-product-id="${product.id}">ADD</button>` :
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

        // Обновляем корзину
        this.updateCartUI();
    }

    /**
     * Настройка UI и обработчиков событий
     */
    setupUI() {
        // Кнопки товаров
        document.addEventListener('click', (e) => {
            const productId = e.target.dataset.productId;
            if (!productId) return;

            console.log('Button clicked:', e.target.className, 'Product ID:', productId);

            const product = this.products.find(p => p.id === parseInt(productId));
            if (!product) {
                console.error('Product not found:', productId);
                return;
            }

            if (e.target.classList.contains('add-button')) {
                console.log('Adding product via ADD button');
                this.addToCart(product, 1);
            } else if (e.target.classList.contains('plus')) {
                console.log('Adding product via PLUS button');
                this.addToCart(product, 1);
            } else if (e.target.classList.contains('minus')) {
                console.log('Removing product via MINUS button');
                this.removeFromCart(product.id, 1);
            }

            // Haptic feedback
            if (this.tg?.HapticFeedback) {
                this.tg.HapticFeedback.impactOccurred('light');
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

        this.updateCartTotals();
        this.saveCartToStorage();
        this.renderProducts(); // Обновляем отображение с правильными количествами
        
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
        this.renderProducts(); // Обновляем отображение
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

        // Обновляем счетчики
        const cartCountElements = document.querySelectorAll('#cart-count');
        cartCountElements.forEach(el => el.textContent = cartCount);

        const cartTotalElements = document.querySelectorAll('#cart-total');
        cartTotalElements.forEach(el => el.textContent = `₽${totalAmount}`);

        // Показываем/скрываем bottom bar
        const bottomBar = document.getElementById('bottom-bar');
        if (bottomBar) {
            bottomBar.style.display = cartCount > 0 ? 'block' : 'none';
        }

        // Обновляем содержимое корзины
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
                    <div class="cart-item-subtitle">Meat™</div>
                </div>
                <div class="cart-item-quantity">${item.quantity}x</div>
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

        if (this.tg?.HapticFeedback) {
            this.tg.HapticFeedback.impactOccurred('medium');
        }
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

        if (this.tg?.HapticFeedback) {
            this.tg.HapticFeedback.impactOccurred('heavy');
        }

        // Запрашиваем контактную информацию если доступно
        if (this.tg?.requestContact) {
            try {
                await this.requestUserContact();
                return; // Продолжение в handleContactReceived
            } catch (error) {
                console.warn('Не удалось запросить контакт, используем стандартные данные');
            }
        }

        // Создаем заказ со стандартными данными
        await this.createOrderWithData({
            deliveryAddress: 'г. Волжск, адрес будет уточнен',
            deliveryType: 'Доставка курьером',
            contactName: this.tg?.initDataUnsafe?.user?.first_name || 'Пользователь',
            contactPhone: '+79999999999',
            comment: 'Заказ через Telegram Mini App',
            paymentMethod: 'SBP'
        });
    }

    /**
     * Запрос контактной информации пользователя
     */
    async requestUserContact() {
        if (!this.tg?.requestContact) {
            throw new Error('requestContact не поддерживается');
        }

        console.log('📞 Запрашиваем контактную информацию...');
        
        return new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                reject(new Error('Таймаут запроса контакта'));
            }, 30000);

            this.contactPromise = { resolve, reject, timeout };
            this.tg.requestContact();
        });
    }

    /**
     * Обработка полученной контактной информации
     */
    handleContactReceived(data) {
        console.log('📞 Получена контактная информация:', data);

        if (this.contactPromise) {
            clearTimeout(this.contactPromise.timeout);
            
            if (data.status === 'sent') {
                const contactData = {
                    deliveryAddress: 'г. Волжск, адрес будет уточнен',
                    deliveryType: 'Доставка курьером',
                    contactName: data.contact?.first_name || this.tg?.initDataUnsafe?.user?.first_name || 'Пользователь',
                    contactPhone: data.contact?.phone_number || '+79999999999',
                    comment: 'Заказ через Telegram Mini App',
                    paymentMethod: 'SBP'
                };

                this.createOrderWithData(contactData);
                this.contactPromise.resolve(contactData);
            } else {
                this.contactPromise.reject(new Error('Контакт не предоставлен'));
            }
            
            this.contactPromise = null;
        }
    }

    /**
     * Создание заказа с данными
     */
    async createOrderWithData(orderData) {
        try {
            console.log('📝 Создаем заказ с данными:', orderData);

            // Подготавливаем данные заказа
            const orderRequest = {
                ...orderData,
                items: this.cart.items.map(item => ({
                    productId: item.productId,
                    quantity: item.quantity,
                    price: item.price
                }))
            };

            // Создаем заказ
            const order = await this.api.createOrder(orderRequest);
            
            // Создаем платеж
            const payment = await this.api.createPayment(order.id, 'SBP');
            
            if (payment.success && payment.confirmationUrl) {
                // Открываем страницу оплаты
                this.tg?.openLink(payment.confirmationUrl);
                
                // Очищаем корзину
                this.cart = { items: [], totalAmount: 0 };
                this.saveCartToStorage();
                this.updateCartUI();
                this.closeCart();
                
                // Показываем уведомление
                this.tg?.showAlert('Заказ создан! Переходим к оплате...');
                
            } else {
                throw new Error(payment.message || 'Ошибка создания платежа');
            }
            
        } catch (error) {
            console.error('❌ Checkout failed:', error);
            this.showError('Ошибка оформления заказа: ' + error.message);
        }
    }

    /**
     * Сохранение корзины в localStorage
     */
    saveCartToStorage() {
        try {
            localStorage.setItem('pizzanat_cart', JSON.stringify(this.cart));
        } catch (error) {
            console.warn('Failed to save cart to localStorage:', error);
        }
    }

    /**
     * Загрузка корзины из localStorage
     */
    loadCartFromStorage() {
        try {
            const saved = localStorage.getItem('pizzanat_cart');
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
    new PizzaNatMenuApp();
});
