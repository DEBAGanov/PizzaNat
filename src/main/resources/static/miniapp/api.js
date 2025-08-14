/**
 * PizzaNat Mini App API Module
 * Handles all API communication with the backend
 */

class PizzaAPI {
    constructor() {
        // API base URL - определяется автоматически
        this.baseURL = window.location.origin;
        this.apiPath = '/api/v1';
        
        // Auth token
        this.authToken = localStorage.getItem('pizzanat_token');
        
        // Request timeout
        this.timeout = 30000;
        
        console.log('🍕 PizzaAPI initialized with base URL:', this.baseURL);
    }

    /**
     * Установка токена авторизации
     */
    setAuthToken(token) {
        this.authToken = token;
        localStorage.setItem('pizzanat_token', token);
    }

    /**
     * Авторизация через Telegram WebApp
     */
    async authenticateWebApp(initDataRaw) {
        console.log('🔐 Authenticating via Telegram WebApp...');
        
        try {
            const response = await this.makeRequest('/telegram-webapp/auth', {
                method: 'POST',
                body: JSON.stringify({
                    initDataRaw: initDataRaw,
                    deviceId: this.getDeviceId(),
                    userAgent: navigator.userAgent,
                    platform: 'telegram-miniapp'
                })
            });

            if (response.token) {
                this.authToken = response.token;
                localStorage.setItem('pizzanat_token', this.authToken);
                console.log('✅ Authentication successful, user ID:', response.userId);
                return response;
            } else {
                throw new Error('No token received');
            }
        } catch (error) {
            console.error('❌ Authentication failed:', error);
            throw error;
        }
    }

    /**
     * Получение категорий
     */
    async getCategories() {
        console.log('📂 Loading categories...');
        return this.makeRequest('/categories', { requiresAuth: false });
    }

    /**
     * Получение продуктов
     */
    async getProducts(categoryId = null, page = 0, size = 20) {
        console.log('🍕 Loading products...', { categoryId, page, size });
        
        let url = `/products?page=${page}&size=${size}`;
        if (categoryId) {
            url = `/products/category/${categoryId}`;
        }
        
        const response = await this.makeRequest(url, { requiresAuth: false });
        // API возвращает объект с полем content (пагинация)
        return response.content || response || [];
    }

    /**
     * Получение продуктов по категории
     */
    async getProductsByCategory(categoryId) {
        console.log('🍕 Loading products by category:', categoryId);
        const response = await this.makeRequest(`/products/category/${categoryId}`, { requiresAuth: false });
        // API возвращает объект с полем content (пагинация)
        return response.content || response || [];
    }

    /**
     * Поиск продуктов
     */
    async searchProducts(query) {
        console.log('🔍 Searching products:', query);
        return this.makeRequest(`/products/search?query=${encodeURIComponent(query)}`);
    }

    /**
     * Получение корзины
     */
    async getCart() {
        console.log('🛒 Loading cart...');
        return this.makeRequest('/cart');
    }

    /**
     * Добавление в корзину
     */
    async addToCart(productId, quantity = 1, selectedOptions = {}) {
        console.log('➕ Adding to cart:', { productId, quantity, selectedOptions });
        
        return this.makeRequest('/cart/items', {
            method: 'POST',
            body: JSON.stringify({
                productId: productId,
                quantity: quantity,
                selectedOptions: selectedOptions
            })
        });
    }

    /**
     * Обновление количества в корзине
     */
    async updateCartItem(productId, quantity) {
        console.log('🔄 Updating cart item:', { productId, quantity });
        
        return this.makeRequest(`/cart/items/${productId}`, {
            method: 'PUT',
            body: JSON.stringify({ quantity: quantity })
        });
    }

    /**
     * Удаление из корзины
     */
    async removeFromCart(productId) {
        console.log('🗑️ Removing from cart:', productId);
        
        return this.makeRequest(`/cart/items/${productId}`, {
            method: 'DELETE'
        });
    }

    /**
     * Очистка корзины
     */
    async clearCart() {
        console.log('🧹 Clearing cart...');
        
        return this.makeRequest('/cart', {
            method: 'DELETE'
        });
    }

    /**
     * Создание заказа
     */
    async createOrder(orderData) {
        console.log('📝 Creating order...', orderData);
        
        return this.makeRequest('/orders', {
            method: 'POST',
            body: JSON.stringify(orderData)
        });
    }

    /**
     * Получение заказов пользователя
     */
    async getUserOrders() {
        console.log('📋 Loading user orders...');
        return this.makeRequest('/orders');
    }

    /**
     * Получение информации о заказе
     */
    async getOrder(orderId) {
        console.log('📋 Loading order:', orderId);
        return this.makeRequest(`/orders/${orderId}`);
    }

    /**
     * Создание платежа YooKassa
     */
    async createPayment(orderId, method = 'SBP', bankId = null) {
        console.log('💳 Creating payment...', { orderId, method, bankId });
        
        return this.makeRequest('/payments/yookassa/create', {
            method: 'POST',
            body: JSON.stringify({
                orderId: orderId,
                method: method,
                bankId: bankId,
                returnUrl: `https://t.me/DIMBOpizzaBot/DIMBO`
            })
        });
    }

    /**
     * Получение профиля пользователя
     */
    async getUserProfile() {
        console.log('👤 Loading user profile...');
        return this.makeRequest('/user/profile');
    }

    /**
     * Получение банков СБП
     */
    async getSbpBanks() {
        console.log('🏦 Loading SBP banks...');
        return this.makeRequest('/payments/yookassa/sbp-banks');
    }

    /**
     * Получение подсказок адресов
     */
    async getAddressSuggestions(query) {
        console.log('🔍 Loading address suggestions for:', query);
        return this.makeRequest(`/address/suggestions?query=${encodeURIComponent(query)}`);
    }

    /**
     * Расчет стоимости доставки
     */
    async calculateDeliveryCost(address, orderAmount = 0) {
        console.log('💰 Calculating delivery cost for:', address, 'Order amount:', orderAmount);
        return this.makeRequest(`/delivery/estimate?address=${encodeURIComponent(address)}&orderAmount=${orderAmount}`);
    }

    /**
     * Получение пунктов доставки
     */
    async getDeliveryLocations() {
        console.log('📍 Loading delivery locations...');
        return this.makeRequest('/delivery-locations');
    }

    /**
     * Базовый метод для выполнения запросов
     */
    async makeRequest(endpoint, options = {}) {
        const url = `${this.baseURL}${this.apiPath}${endpoint}`;
        
        const defaultOptions = {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        };

        // Добавляем авторизацию если есть токен
        if (this.authToken) {
            defaultOptions.headers['Authorization'] = `Bearer ${this.authToken}`;
        }

        const finalOptions = {
            ...defaultOptions,
            ...options,
            headers: {
                ...defaultOptions.headers,
                ...options.headers
            }
        };

        console.log(`🌐 Making request: ${finalOptions.method} ${url}`);

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), this.timeout);

            const response = await fetch(url, {
                ...finalOptions,
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const errorText = await response.text();
                console.error(`❌ Request failed: ${response.status} ${response.statusText}`, errorText);
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const data = await response.json();
                console.log(`✅ Request successful: ${finalOptions.method} ${url}`, data);
                return data;
            } else {
                const text = await response.text();
                console.log(`✅ Request successful (text): ${finalOptions.method} ${url}`, text);
                return text;
            }

        } catch (error) {
            if (error.name === 'AbortError') {
                console.error('⏰ Request timeout:', url);
                throw new Error('Время ожидания истекло. Проверьте соединение.');
            }
            
            console.error('❌ Request error:', error);
            throw error;
        }
    }

    /**
     * Получение ID устройства
     */
    getDeviceId() {
        let deviceId = localStorage.getItem('pizzanat_device_id');
        if (!deviceId) {
            deviceId = 'tgwa_' + Math.random().toString(36).substr(2, 9) + Date.now().toString(36);
            localStorage.setItem('pizzanat_device_id', deviceId);
        }
        return deviceId;
    }

    /**
     * Очистка токена авторизации
     */
    clearAuth() {
        this.authToken = null;
        localStorage.removeItem('pizzanat_token');
        console.log('🔓 Auth cleared');
    }

    /**
     * Проверка авторизации
     */
    isAuthenticated() {
        return !!this.authToken;
    }

    /**
     * Форматирование цены
     */
    formatPrice(price) {
        return new Intl.NumberFormat('ru-RU', {
            style: 'currency',
            currency: 'RUB',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(price);
    }

    /**
     * Debounce функция для оптимизации запросов
     */
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
}

// Создаем глобальный экземпляр API
window.PizzaAPI = new PizzaAPI();

console.log('🍕 PizzaNat API module loaded');
