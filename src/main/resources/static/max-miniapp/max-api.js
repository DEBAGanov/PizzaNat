/**
 * PizzaNat MAX Mini App API Module
 * Handles all API communication with the backend for MAX messenger
 *
 * Key differences from Telegram:
 * - Authentication endpoint: /max-webapp/auth instead of /telegram-webapp/auth
 * - No requestContact API - phone collected manually
 */

class MaxPizzaAPI {
    constructor() {
        // API base URL
        this.baseURL = 'https://api.dimbopizza.ru';
        this.apiPath = '/api/v1';

        // Auth token
        this.authToken = localStorage.getItem('pizzanat_max_token');

        // Session ID for cart
        this.sessionId = localStorage.getItem('pizzanat_max_session_id');

        // Request timeout
        this.timeout = 30000;

        console.log('🍕 MaxPizzaAPI initialized with base URL:', this.baseURL);
    }

    /**
     * Сохранение sessionId
     */
    setSessionId(sessionId) {
        this.sessionId = sessionId;
        localStorage.setItem('pizzanat_max_session_id', sessionId);
        console.log('🔑 Session ID saved:', sessionId);
    }

    /**
     * Установка токена авторизации
     */
    setAuthToken(token) {
        this.authToken = token;
        localStorage.setItem('pizzanat_max_token', token);
    }

    /**
     * Авторизация через MAX WebApp
     */
    async authenticateWebApp(initDataRaw, phoneNumber = null) {
        console.log('🔐 Authenticating via MAX WebApp...');

        try {
            const requestBody = {
                initDataRaw: initDataRaw,
                deviceId: this.getDeviceId(),
                platform: 'max-miniapp'
            };

            // Добавляем номер телефона если предоставлен
            if (phoneNumber && phoneNumber.trim()) {
                requestBody.phoneNumber = phoneNumber.trim();
            }

            const response = await this.makeRequest('/max-webapp/auth', {
                method: 'POST',
                body: JSON.stringify(requestBody)
            });

            if (response.token) {
                this.authToken = response.token;
                localStorage.setItem('pizzanat_max_token', this.authToken);
                console.log('✅ MAX Authentication successful, user ID:', response.userId);
                return response;
            } else {
                throw new Error('No token received');
            }
        } catch (error) {
            console.error('❌ MAX Authentication failed:', error);
            throw error;
        }
    }

    /**
     * Валидация MAX initData
     */
    async validateInitData(initDataRaw) {
        console.log('🔍 Validating MAX initData...');

        try {
            const response = await this.makeRequest('/max-webapp/validate', {
                method: 'POST',
                body: JSON.stringify({ initDataRaw: initDataRaw })
            });
            return response === true;
        } catch (error) {
            console.error('❌ MAX validation failed:', error);
            return false;
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
        return response.content || response || [];
    }

    /**
     * Получение продуктов по категории
     */
    async getProductsByCategory(categoryId) {
        console.log('🍕 Loading products by category:', categoryId);
        const response = await this.makeRequest(`/products/category/${categoryId}`, { requiresAuth: false });
        return response.content || response || [];
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
     * Получение последнего адреса доставки пользователя
     */
    async getLastDeliveryAddress() {
        console.log('📍 Loading last delivery address...');
        try {
            const orders = await this.getUserOrders();
            if (orders && orders.length > 0) {
                const lastOrderWithAddress = orders.find(order => order.deliveryAddress);
                if (lastOrderWithAddress) {
                    return {
                        address: lastOrderWithAddress.deliveryAddress,
                        deliveryCost: lastOrderWithAddress.deliveryCost || 200
                    };
                }
            }
            return null;
        } catch (error) {
            console.warn('Could not load last delivery address:', error);
            return null;
        }
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
                returnUrl: `https://max.ru/app?id121603899498_bot`
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
     * Расчет стоимости доставки
     */
    async calculateDeliveryCost(address, orderAmount = 0) {
        console.log('💰 Calculating delivery cost for:', address, 'Order amount:', orderAmount);
        return this.makeRequest(`/delivery/estimate?address=${encodeURIComponent(address)}&orderAmount=${orderAmount}`);
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

        // Добавляем sessionId если есть (для корзины)
        if (this.sessionId) {
            defaultOptions.headers['X-Session-Id'] = this.sessionId;
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

            // Извлекаем sessionId из заголовка ответа если есть
            const responseSessionId = response.headers.get('X-Session-Id');
            if (responseSessionId && responseSessionId !== this.sessionId) {
                this.setSessionId(responseSessionId);
            }

            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const data = await response.json();

                // Извлекаем sessionId из ответа (для корзины)
                if (data && data.sessionId && data.sessionId !== this.sessionId) {
                    this.setSessionId(data.sessionId);
                }

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
        let deviceId = localStorage.getItem('pizzanat_max_device_id');
        if (!deviceId) {
            deviceId = 'max_' + Math.random().toString(36).substr(2, 9) + Date.now().toString(36);
            localStorage.setItem('pizzanat_max_device_id', deviceId);
        }
        return deviceId;
    }

    /**
     * Очистка токена авторизации
     */
    clearAuth() {
        this.authToken = null;
        localStorage.removeItem('pizzanat_max_token');
        console.log('🔓 MAX Auth cleared');
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

// Создаем глобальный экземпляр API для MAX
window.MaxPizzaAPI = new MaxPizzaAPI();

console.log('🍕 PizzaNat MAX API module loaded');
