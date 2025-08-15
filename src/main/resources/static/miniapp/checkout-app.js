/**
 * PizzaNat Mini App - Checkout Page
 * Order processing with delivery and payment selection
 */

class PizzaNatCheckoutApp {
    constructor() {
        this.tg = window.Telegram?.WebApp;
        this.api = window.PizzaAPI;
        this.cart = { items: [], totalAmount: 0 };
        this.deliveryMethod = 'DELIVERY'; // Default to delivery
        this.paymentMethod = 'SBP'; // Default to SBP
        this.deliveryCost = 200; // Default delivery cost
        this.address = '';
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
        console.log('🚀 Initializing PizzaNat Checkout...');
        
        try {
            // Настройка Telegram WebApp
            this.setupTelegramWebApp();
            
            // Авторизация
            await this.authenticate();
            
            // Проверка корзины
            console.log('🛒 Cart check: items =', this.cart.items.length);
            if (this.cart.items.length === 0) {
                console.warn('⚠️ Empty cart detected, adding test items for development');
                // Добавляем тестовые товары для отладки
                this.cart.items = [
                    {
                        productId: 1,
                        name: 'Тестовая пицца',
                        price: 500,
                        quantity: 1,
                        imageUrl: 'https://via.placeholder.com/100'
                    }
                ];
                this.cart.totalAmount = 500;
                this.saveCartToStorage();
            }
            
            // Настройка UI
            this.setupUI();
            
            // Загрузка данных
            await this.loadUserData();
            
            // Показываем приложение
            this.showApp();
            
            console.log('✅ Checkout initialized successfully');
            
        } catch (error) {
            console.error('❌ Checkout initialization failed:', error);
            this.showError('Ошибка загрузки формы заказа');
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
                window.history.back();
            });
        }
        
        // Подписываемся на события
        this.tg.onEvent('themeChanged', () => this.applyTelegramTheme());
        
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
        console.log('🔐 Starting authentication...');
        console.log('📱 Telegram WebApp available:', !!this.tg);
        console.log('📋 Telegram initData available:', !!this.tg?.initData);
        
        if (!this.tg?.initData) {
            console.warn('⚠️ No Telegram initData available - using demo mode');
            return;
        }

        console.log('🔐 Authenticating user with initData...');

        try {
            // Создаем API если его нет
            if (!this.api) {
                this.api = new PizzaAPI();
                console.log('📡 API instance created with baseURL:', this.api.baseURL);
            }
            
            const response = await this.api.authenticateWebApp(this.tg.initData);
            console.log('🔐 Auth response:', response);
            
            this.authToken = response.token;
            
            // Устанавливаем токен в API
            this.api.setAuthToken(this.authToken);
            
            console.log('✅ User authenticated successfully');
        } catch (error) {
            console.error('❌ Authentication failed:', error.message, error);
            console.log('🔧 Continuing without auth...');
            // Продолжаем без авторизации для демонстрации
        }
    }

    /**
     * Загрузка данных пользователя
     */
    async loadUserData() {
        try {
            console.log('📋 Loading user data from auth...');
            
            // Получаем данные пользователя из профиля
            const userProfile = await this.api.getUserProfile();
            console.log('👤 User profile:', userProfile);
            
            if (userProfile) {
                // Формируем полное имя из firstName и lastName
                const fullName = [userProfile.firstName, userProfile.lastName]
                    .filter(part => part && part.trim())
                    .join(' ') || userProfile.displayName || userProfile.username || 'Пользователь';
                
                // Получаем телефон из разных возможных полей
                const phoneNumber = userProfile.phone || userProfile.phoneNumber || '';
                
                // Обновляем отображение имени
                const userNameEl = document.getElementById('user-name');
                if (userNameEl) {
                    userNameEl.textContent = fullName;
                }
                
                // Обновляем отображение телефона
                const userPhoneEl = document.getElementById('user-phone');
                if (userPhoneEl) {
                    userPhoneEl.textContent = phoneNumber || 'Телефон не указан';
                }
                
                // Сохраняем данные для отправки заказа
                this.userData = {
                    name: fullName,
                    phone: phoneNumber
                };
                
                console.log('✅ User data loaded successfully');
            } else {
                console.warn('⚠️ No user profile found');
                this.handleMissingUserData();
            }
            
        } catch (error) {
            console.error('❌ Failed to load user data:', error.message, error);
            console.log('🔧 Trying to work without auth...');
            
            // Попробуем работать без авторизации с тестовыми данными
            const userNameEl = document.getElementById('user-name');
            const userPhoneEl = document.getElementById('user-phone');
            
            if (userNameEl) userNameEl.textContent = 'Пользователь Telegram';
            if (userPhoneEl) userPhoneEl.textContent = 'Требуется номер телефона';
            
            this.userData = {
                name: 'Пользователь Telegram',
                phone: ''
            };
            
            // Запрашиваем номер телефона
            if (this.tg && this.tg.requestContact) {
                console.log('📱 Requesting phone contact from user...');
                this.tg.requestContact();
            }
        }
    }
    
    /**
     * Обработка отсутствующих данных пользователя
     */
    handleMissingUserData() {
        // Показываем сообщение о необходимости авторизации
        const userNameEl = document.getElementById('user-name');
        const userPhoneEl = document.getElementById('user-phone');
        
        if (userNameEl) {
            userNameEl.textContent = 'Данные не загружены';
            userNameEl.style.color = 'var(--tg-theme-destructive-text-color, #ff6b6b)';
        }
        
        if (userPhoneEl) {
            userPhoneEl.textContent = 'Требуется авторизация';
            userPhoneEl.style.color = 'var(--tg-theme-destructive-text-color, #ff6b6b)';
        }
        
        // Запрашиваем номер телефона если его нет
        if (this.tg && this.tg.requestContact) {
            console.log('📱 Requesting phone contact from user...');
            this.tg.requestContact();
        }
        
        // Отключаем кнопку заказа
        const submitButton = document.getElementById('submit-order');
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = 'Требуется авторизация';
        }
    }

    /**
     * Настройка UI и обработчиков событий
     */
    setupUI() {
        // Отображаем товары заказа
        this.renderOrderItems();
        
        // Back button
        document.getElementById('back-button')?.addEventListener('click', () => {
            window.history.back();
        });

        // Delivery method change
        document.querySelectorAll('input[name="deliveryMethod"]').forEach(input => {
            input.addEventListener('change', (e) => {
                this.handleDeliveryMethodChange(e.target.value);
            });
        });

        // Payment method change
        document.querySelectorAll('input[name="paymentMethod"]').forEach(input => {
            input.addEventListener('change', (e) => {
                this.handlePaymentMethodChange(e.target.value);
            });
        });

        // Address input
        const addressInput = document.getElementById('address-input');
        if (addressInput) {
            addressInput.addEventListener('input', this.debounce((e) => {
                this.handleAddressInput(e.target.value);
            }, 300));
        }

        // Submit order
        document.getElementById('submit-order')?.addEventListener('click', () => {
            this.submitOrder();
        });

        // Обработка ввода адреса и подсказок
        this.setupAddressInput();

        // Обработка клавиатуры для мобильных устройств
        this.setupKeyboardHandling();

        // Initialize with default values
        this.handleDeliveryMethodChange(this.deliveryMethod);
        this.handlePaymentMethodChange(this.paymentMethod);
    }

    /**
     * Отображение товаров заказа
     */
    renderOrderItems() {
        const container = document.getElementById('order-items');
        if (!container) return;

        container.innerHTML = '';

        this.cart.items.forEach(item => {
            const itemElement = document.createElement('div');
            itemElement.className = 'order-item';
            itemElement.innerHTML = `
                <img src="${item.imageUrl || '/static/images/products/pizza_4_chees.png'}" 
                     alt="${item.name}" 
                     class="order-item-image">
                <div class="order-item-info">
                    <div class="order-item-title">${item.name}</div>
                    <div class="order-item-details">${item.quantity} шт. × ₽${item.price}</div>
                </div>
                <div class="order-item-price">₽${item.subtotal}</div>
            `;
            container.appendChild(itemElement);
        });

        this.updateTotals();
    }

    /**
     * Обработка изменения способа доставки
     */
    async handleDeliveryMethodChange(method) {
        this.deliveryMethod = method;
        const addressSection = document.getElementById('address-section');

        if (method === 'DELIVERY') {
            // Показываем поле адреса
            addressSection.style.display = 'block';
            this.deliveryCost = 200; // Default delivery cost
            
            // Если адрес уже введен, пересчитываем стоимость
            const address = document.getElementById('address-input')?.value;
            if (address) {
                await this.calculateDeliveryCost(address);
            }
        } else {
            // Скрываем поле адреса
            addressSection.style.display = 'none';
            this.deliveryCost = 0;
        }

        this.updateDeliveryPrice();
        this.updateTotals();
    }

    /**
     * Обработка изменения способа оплаты
     */
    handlePaymentMethodChange(method) {
        this.paymentMethod = method;
        console.log('Payment method changed to:', method);
    }

    /**
     * Обработка ввода адреса
     */
    async handleAddressInput(address) {
        this.address = address;

        if (address.length < 3) return;

        try {
            // Получаем подсказки адресов
            await this.loadAddressSuggestions(address);
            
            // Рассчитываем стоимость доставки
            if (this.deliveryMethod === 'DELIVERY') {
                await this.calculateDeliveryCost(address);
            }
        } catch (error) {
            console.warn('Ошибка при обработке адреса:', error);
        }
    }

    /**
     * Загрузка подсказок адресов
     */
    async loadAddressSuggestions(query) {
        try {
            // Получаем подсказки адресов через API
            const suggestions = await this.api.getAddressSuggestions(query);
            console.log('📍 Address suggestions received:', suggestions);
            
            // Обрабатываем ответ API - извлекаем только краткий адрес
            let formattedSuggestions;
            if (Array.isArray(suggestions)) {
                formattedSuggestions = suggestions.map(suggestion => {
                    if (typeof suggestion === 'string') {
                        return suggestion;
                    } else if (suggestion.shortAddress) {
                        // Используем краткий адрес (только улица и дом)
                        return suggestion.shortAddress;
                    } else if (suggestion.address) {
                        // Извлекаем часть после "Волжск, "
                        const fullAddress = suggestion.address;
                        const volzhskIndex = fullAddress.indexOf('Волжск, ');
                        if (volzhskIndex !== -1) {
                            return fullAddress.substring(volzhskIndex + 8).trim();
                        }
                        return fullAddress;
                    } else if (suggestion.value) {
                        return suggestion.value;
                    } else {
                        return 'Неизвестный адрес';
                    }
                });
            } else {
                formattedSuggestions = [];
            }
            
            this.displayAddressSuggestions(formattedSuggestions);
        } catch (error) {
            console.warn('Ошибка при загрузке подсказок адресов:', error);
            // Fallback to local suggestions
            const fallbackSuggestions = [
                `г. Волжск, ул. ${query}`,
                `г. Волжск, пр. ${query}`,
                `г. Волжск, ${query}`
            ];
            this.displayAddressSuggestions(fallbackSuggestions);
        }
    }

    /**
     * Отображение подсказок адресов
     */
    displayAddressSuggestions(suggestions) {
        const container = document.getElementById('address-suggestions');
        if (!container) return;

        container.innerHTML = '';

        if (suggestions.length === 0) {
            container.style.display = 'none';
            return;
        }

        suggestions.forEach(suggestion => {
            const item = document.createElement('div');
            item.className = 'address-suggestion-item';
            item.textContent = suggestion;
            container.appendChild(item);
        });

        container.style.display = 'block';
    }

    /**
     * Расчет стоимости доставки
     */
    async calculateDeliveryCost(address) {
        try {
            console.log('Calculating delivery cost for:', address);
            
            // Используем API для расчета стоимости доставки
            const data = await this.api.calculateDeliveryCost(address, this.cart.totalAmount);
            console.log('📊 Delivery cost response:', data);
            
            if (data && typeof data.deliveryCost === 'number') {
                this.deliveryCost = data.deliveryCost;
                console.log('✅ Delivery cost calculated:', this.deliveryCost, 'rubles');
            } else if (data && data.deliveryAvailable === false) {
                this.deliveryCost = 0; // Доставка недоступна
                console.warn('⚠️ Delivery not available for this address');
            } else {
                this.deliveryCost = 200; // Default cost
                console.warn('⚠️ Using default delivery cost');
            }
        } catch (error) {
            console.warn('Error calculating delivery cost:', error);
            this.deliveryCost = 200; // Default cost
        }

        this.updateDeliveryPrice();
        this.updateTotals();
    }

    /**
     * Обновление отображения стоимости доставки
     */
    updateDeliveryPrice() {
        const priceElement = document.getElementById('delivery-price');
        if (priceElement) {
            priceElement.textContent = this.deliveryCost > 0 ? `₽${this.deliveryCost}` : 'Бесплатно';
        }
    }

    /**
     * Обновление итоговых сумм
     */
    updateTotals() {
        const itemsTotal = this.cart.totalAmount;
        const totalAmount = itemsTotal + this.deliveryCost;

        // Обновляем отображение
        document.getElementById('items-total').textContent = `₽${itemsTotal}`;
        document.getElementById('delivery-cost').textContent = `₽${this.deliveryCost}`;
        document.getElementById('total-amount').textContent = `₽${totalAmount}`;
        document.getElementById('final-total').textContent = `₽${totalAmount}`;
    }

    /**
     * Настройка обработки ввода адреса и подсказок
     */
    setupAddressInput() {
        const addressInput = document.getElementById('address-input');
        const addressSuggestions = document.getElementById('address-suggestions');
        
        if (!addressInput || !addressSuggestions) return;
        
        // Обработка фокуса на поле ввода
        addressInput.addEventListener('focus', () => {
            document.body.classList.add('keyboard-visible');
            addressSuggestions.classList.add('keyboard-visible');
            
            // Прокручиваем к полю ввода
            setTimeout(() => {
                addressInput.scrollIntoView({ 
                    behavior: 'smooth', 
                    block: 'center' 
                });
            }, 300);
        });
        
        // Обработка потери фокуса
        addressInput.addEventListener('blur', () => {
            setTimeout(() => {
                document.body.classList.remove('keyboard-visible');
                addressSuggestions.classList.remove('keyboard-visible');
            }, 150); // Небольшая задержка для обработки кликов по подсказкам
        });
        
        // Обработка кликов по подсказкам
        addressSuggestions.addEventListener('click', async (e) => {
            const suggestionItem = e.target.closest('.address-suggestion-item');
            if (suggestionItem) {
                const selectedAddress = suggestionItem.textContent.trim();
                addressInput.value = selectedAddress;
                this.address = selectedAddress;
                addressSuggestions.innerHTML = '';
                addressInput.blur();
                
                // Обновляем стоимость доставки
                console.log('🚗 Calculating delivery cost for selected address:', selectedAddress);
                await this.calculateDeliveryCost(selectedAddress);
            }
        });
    }
    
    /**
     * Настройка обработки клавиатуры для мобильных устройств
     */
    setupKeyboardHandling() {
        if (!this.tg) return;
        
        // Обработка изменения высоты viewport (появление/скрытие клавиатуры)
        let initialViewportHeight = window.visualViewport?.height || window.innerHeight;
        
        const handleViewportChange = () => {
            const currentHeight = window.visualViewport?.height || window.innerHeight;
            const heightDiff = initialViewportHeight - currentHeight;
            
            if (heightDiff > 150) { // Клавиатура появилась
                document.body.classList.add('keyboard-visible');
            } else { // Клавиатура скрылась
                document.body.classList.remove('keyboard-visible');
            }
        };
        
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', handleViewportChange);
        } else {
            window.addEventListener('resize', handleViewportChange);
        }
    }
    
    /**
     * Оформление заказа
     */
    async submitOrder() {
        try {
            // Validation - проверяем корзину
            if (!this.cart.items || this.cart.items.length === 0) {
                this.showError('Корзина пуста. Добавьте товары для оформления заказа');
                return;
            }

            // Validation - проверяем данные пользователя из авторизации
            if (!this.userData || !this.userData.name) {
                this.showError('Данные пользователя не загружены. Пожалуйста, авторизуйтесь');
                return;
            }

            if (!this.userData.phone) {
                this.showError('Номер телефона не указан. Пожалуйста, обновите профиль');
                return;
            }

            if (this.deliveryMethod === 'DELIVERY' && !this.address) {
                this.showError('Укажите адрес доставки');
                return;
            }

            // Disable submit button
            const submitButton = document.getElementById('submit-order');
            submitButton.disabled = true;
            submitButton.textContent = 'Оформляем заказ...';

            // Подготавливаем данные заказа согласно API (как в тестах)
            const orderData = {
                contactName: this.userData.name,
                contactPhone: this.userData.phone,
                comment: document.getElementById('order-comment')?.value.trim() || '',
                paymentMethod: this.paymentMethod,
                // Добавляем товары из корзины
                items: this.cart.items.map(item => ({
                    productId: item.productId,
                    quantity: item.quantity,
                    price: item.price
                }))
            };

            // Добавляем данные доставки в зависимости от выбранного метода
            if (this.deliveryMethod === 'DELIVERY') {
                orderData.deliveryAddress = this.address;
                orderData.deliveryType = 'Доставка курьером';
                orderData.deliveryCost = this.deliveryCost;
            } else {
                orderData.deliveryLocationId = 1; // ID самовывоза
                orderData.deliveryType = 'Самовывоз';
                orderData.deliveryCost = 0;
            }

            console.log('Creating order with data:', orderData);

            // Сначала очищаем корзину на бэкенде и добавляем все товары
            console.log('🛒 Adding items to backend cart...');
            await this.api.clearCart();
            
            for (const item of this.cart.items) {
                await this.api.addToCart(item.productId, item.quantity);
            }

            // Создаем заказ (без поля items - бэкенд возьмет из корзины)
            const orderDataForAPI = {
                contactName: orderData.contactName,
                contactPhone: orderData.contactPhone,
                comment: orderData.comment,
                paymentMethod: orderData.paymentMethod
            };

            // Добавляем данные доставки
            if (this.deliveryMethod === 'DELIVERY') {
                orderDataForAPI.deliveryAddress = this.address;
                orderDataForAPI.deliveryType = 'Доставка курьером';
                orderDataForAPI.deliveryCost = this.deliveryCost;
            } else {
                orderDataForAPI.deliveryLocationId = 1;
                orderDataForAPI.deliveryType = 'Самовывоз';
                orderDataForAPI.deliveryCost = 0;
            }

            console.log('Creating order with backend cart data:', orderDataForAPI);
            const order = await this.api.createOrder(orderDataForAPI);
            
            if (this.paymentMethod === 'SBP') {
                // Создаем платеж для СБП
                const payment = await this.api.createPayment(order.id, 'SBP');
                
                if (payment.success && payment.confirmationUrl) {
                    // Очищаем корзину
                    this.clearCart();
                    
                    // Открываем страницу оплаты
                    this.tg?.openLink(payment.confirmationUrl);
                    
                    // Показываем уведомление
                    this.tg?.showAlert('Заказ создан! Переходим к оплате...');
                    
                } else {
                    throw new Error(payment.message || 'Ошибка создания платежа');
                }
            } else {
                // Для наличной оплаты показываем успех
                this.clearCart();
                this.tg?.showAlert('Заказ успешно оформлен! Мы свяжемся с вами для подтверждения. Уведомления о статусе придут в чат.');
                
                // Возвращаемся в меню
                setTimeout(() => {
                    window.location.href = 'menu.html';
                }, 3000);
            }
            
        } catch (error) {
            console.error('❌ Order submission failed:', error);
            this.showError('Ошибка оформления заказа: ' + error.message);
            
            // Re-enable submit button
            const submitButton = document.getElementById('submit-order');
            submitButton.disabled = false;
            this.updateTotals(); // This will update the button text
        }
    }

    /**
     * Очистка корзины
     */
    clearCart() {
        this.cart = { items: [], totalAmount: 0 };
        localStorage.removeItem('pizzanat_cart');
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
        if (this.tg?.showAlert) {
            this.tg.showAlert(message);
        } else {
            alert(message);
        }
    }

    /**
     * Debounce функция
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

// Делаем класс доступным глобально
window.PizzaNatCheckoutApp = PizzaNatCheckoutApp;
