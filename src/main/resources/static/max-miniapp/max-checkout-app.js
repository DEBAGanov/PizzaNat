/**
 * PizzaNat MAX Mini App - Checkout Page
 * Order processing with delivery and payment selection for MAX messenger
 *
 * Key difference: MAX doesn't have requestContact API, so phone is collected manually
 */

// E-commerce tracking functions
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

function trackPurchase(orderData, items) {
    const ecommerceData = {
        purchase: {
            transaction_id: orderData.id || orderData.orderId,
            value: orderData.totalAmount,
            currency: 'RUB',
            items: items.map(item => ({
                item_id: item.productId?.toString(),
                item_name: item.name,
                category: item.category || 'Еда',
                quantity: item.quantity,
                price: item.price
            }))
        }
    };

    trackEcommerce('purchase', ecommerceData);

    const productIds = items.map(item => item.productId?.toString());
    trackVKEcommerce('purchase', {
        value: orderData.totalAmount,
        params: {
            product_id: productIds.length === 1 ? productIds[0] : productIds
        }
    });
}

function trackBeginCheckout(items, totalAmount) {
    const ecommerceData = {
        begin_checkout: {
            value: totalAmount,
            currency: 'RUB',
            items: items.map(item => ({
                item_id: item.productId?.toString(),
                item_name: item.name,
                category: item.category || 'Еда',
                quantity: item.quantity,
                price: item.price
            }))
        }
    };

    trackEcommerce('begin_checkout', ecommerceData);

    const productIds = items.map(item => item.productId?.toString());
    trackVKEcommerce('initiate_checkout', {
        value: totalAmount,
        params: {
            product_id: productIds.length === 1 ? productIds[0] : productIds
        }
    });
}

class PizzaNatMaxCheckoutApp {
    constructor() {
        // MAX WebApp
        this.max = window.WebApp;
        this.api = null;
        this.cart = { items: [], totalAmount: 0 };
        this.deliveryMethod = 'DELIVERY';
        this.paymentMethod = 'SBP';
        this.deliveryCost = 200;
        this.address = '';
        this.authToken = null;
        this.userData = null;

        // Load cart from localStorage
        this.loadCartFromStorage();

        // Initialize app
        this.init();
    }

    /**
     * Инициализация приложения
     */
    async init() {
        console.log('🚀 Initializing PizzaNat MAX Checkout...');

        try {
            // Инициализация API
            if (window.MaxPizzaAPI) {
                this.api = window.MaxPizzaAPI;
            } else {
                this.api = new MaxPizzaAPI();
            }
            console.log('📡 API initialized:', this.api.baseURL);

            // Настройка MAX WebApp
            this.setupMaxWebApp();

            // Авторизация
            await this.authenticate();

            // Проверка корзины
            console.log('🛒 Cart check: items =', this.cart.items.length);
            if (this.cart.items.length === 0) {
                console.warn('⚠️ Empty cart detected');
            }

            // Настройка UI
            this.setupUI();

            // Загрузка данных
            await this.loadUserData();

            // Отслеживание начала оформления заказа
            if (this.cart.items && this.cart.items.length > 0) {
                trackBeginCheckout(this.cart.items, this.cart.totalAmount);
            }

            // Показываем приложение
            this.showApp();

            console.log('✅ MAX Checkout initialized successfully');

        } catch (error) {
            console.error('❌ MAX Checkout initialization failed:', error);
            this.showError(`Ошибка загрузки формы заказа: ${error.message}`);
        }
    }

    /**
     * Настройка MAX WebApp
     */
    setupMaxWebApp() {
        if (!this.max) {
            console.warn('⚠️ MAX WebApp API not available');
            return;
        }

        console.log('📱 Setting up MAX WebApp...');

        // Разворачиваем приложение
        if (this.max.expand) {
            try {
                this.max.expand();
            } catch (e) {}
        }

        // Настраиваем тему
        this.applyMaxTheme();

        // Подписываемся на события
        if (this.max.on) {
            this.max.on('WebAppThemeChanged', () => this.applyMaxTheme());
        }

        console.log('✅ MAX WebApp configured');
    }

    /**
     * Применение темы MAX
     */
    applyMaxTheme() {
        const root = document.documentElement;

        root.style.setProperty('--tg-theme-bg-color', '#1a1a1a');
        root.style.setProperty('--tg-theme-text-color', '#ffffff');
        root.style.setProperty('--tg-theme-button-color', '#ff6b35');
        root.style.setProperty('--tg-theme-button-text-color', '#ffffff');
    }

    /**
     * Авторизация пользователя
     */
    async authenticate() {
        if (!this.max?.initData) {
            console.warn('⚠️ No MAX initData available - using demo mode');
            return;
        }

        console.log('🔐 Authenticating user...');

        try {
            const response = await this.api.authenticateWebApp(this.max.initData);
            this.authToken = response.token;
            this.api.setAuthToken(this.authToken);

            console.log('✅ User authenticated');
        } catch (error) {
            console.error('❌ Authentication failed:', error);
        }
    }

    /**
     * Загрузка данных пользователя
     */
    async loadUserData() {
        try {
            console.log('📋 Loading user data from auth...');

            const userProfile = await this.api.getUserProfile();
            console.log('👤 User profile:', userProfile);

            if (userProfile) {
                const fullName = [userProfile.firstName, userProfile.lastName]
                    .filter(part => part && part.trim())
                    .join(' ') || userProfile.displayName || userProfile.username || 'Пользователь';

                const phoneNumber = userProfile.phone || userProfile.phoneNumber || '';

                const userNameEl = document.getElementById('user-name');
                if (userNameEl) {
                    userNameEl.textContent = fullName;
                }

                if (phoneNumber && phoneNumber.length > 0) {
                    this.displayExistingPhoneNumber(phoneNumber);
                } else {
                    // MAX не имеет requestContact - показываем поле ввода
                    this.showManualPhoneInput();
                }

                this.userData = {
                    name: fullName,
                    phone: phoneNumber
                };

                this.updateSubmitButtonState();

                console.log('✅ User data loaded successfully', { hasPhone: !!phoneNumber });
            } else {
                this.handleMissingUserData();
            }

        } catch (error) {
            console.error('❌ Failed to load user data:', error.message);
            this.handleMissingUserData();
        }
    }

    /**
     * Обработка отсутствующих данных пользователя
     */
    handleMissingUserData() {
        const maxUser = this.max?.initDataUnsafe?.user;
        const fallbackName = maxUser ?
            [maxUser.first_name, maxUser.last_name].filter(Boolean).join(' ') :
            'Пользователь MAX';

        const userNameEl = document.getElementById('user-name');
        if (userNameEl) {
            userNameEl.textContent = fallbackName;
        }

        this.userData = {
            name: fallbackName,
            phone: ''
        };

        // Показываем поле для ввода телефона
        this.showManualPhoneInput();
        this.updateSubmitButtonState();
    }

    /**
     * Показать поле для ручного ввода номера телефона
     */
    showManualPhoneInput() {
        console.log('📝 Showing phone input (MAX has no requestContact API)...');

        // Проверяем, есть ли уже номер
        if (this.userData?.phone && this.userData.phone.length > 0) {
            this.displayExistingPhoneNumber(this.userData.phone);
            return;
        }

        const userPhoneEl = document.getElementById('user-phone');
        if (userPhoneEl) {
            userPhoneEl.innerHTML = `
                <input type="tel"
                       id="manual-phone-input"
                       placeholder="+7 XXX XXX XX XX"
                       value="+7 "
                       style="width: 100%; padding: 12px; border: 1px solid #ccc; border-radius: 8px; box-sizing: border-box; font-size: 16px;"
                       maxlength="18">
                <button onclick="window.maxCheckoutApp.submitManualPhone()"
                        style="width: 100%; margin-top: 8px; padding: 12px 16px; background: #28a745; color: white; border: none; border-radius: 8px; font-size: 16px; cursor: pointer;">
                    ✅ Подтвердить номер
                </button>
            `;

            const phoneInput = document.getElementById('manual-phone-input');
            if (phoneInput) {
                this.setupPhoneInputFormatting(phoneInput);
            }
        }
    }

    /**
     * Отображение существующего номера телефона
     */
    displayExistingPhoneNumber(phoneNumber) {
        console.log('📞 Displaying existing phone:', phoneNumber);

        const userPhoneEl = document.getElementById('user-phone');
        if (userPhoneEl) {
            userPhoneEl.innerHTML = '';
            userPhoneEl.textContent = phoneNumber;
            userPhoneEl.style.color = 'var(--tg-theme-text-color, #000000)';
            userPhoneEl.style.fontWeight = 'normal';
        }
    }

    /**
     * Настройка форматирования номера телефона
     */
    setupPhoneInputFormatting(phoneInput) {
        phoneInput.addEventListener('input', (e) => {
            let value = e.target.value;
            const digits = value.replace(/[^\d+]/g, '');

            if (!digits.startsWith('+7')) {
                if (digits.length === 0) {
                    e.target.value = '+7 ';
                    return;
                }
                if (digits.startsWith('7')) {
                    value = '+' + digits;
                } else if (digits.startsWith('8')) {
                    value = '+7' + digits.substring(1);
                } else {
                    value = '+7' + digits;
                }
            } else {
                value = digits;
            }

            const withoutPrefix = value.substring(2);
            if (withoutPrefix.length > 10) {
                value = '+7' + withoutPrefix.substring(0, 10);
            }

            if (value.length > 2) {
                let formatted = '+7';
                const phoneDigits = value.substring(2);

                if (phoneDigits.length > 0) {
                    formatted += ' ' + phoneDigits.substring(0, 3);
                }
                if (phoneDigits.length > 3) {
                    formatted += ' ' + phoneDigits.substring(3, 6);
                }
                if (phoneDigits.length > 6) {
                    formatted += ' ' + phoneDigits.substring(6, 8);
                }
                if (phoneDigits.length > 8) {
                    formatted += ' ' + phoneDigits.substring(8, 10);
                }

                e.target.value = formatted;
            }

            setTimeout(() => {
                e.target.setSelectionRange(e.target.value.length, e.target.value.length);
            }, 0);
        });

        phoneInput.addEventListener('keydown', (e) => {
            const cursorPosition = e.target.selectionStart;
            const value = e.target.value;

            if ((e.key === 'Backspace' || e.key === 'Delete') && cursorPosition <= 3 && value.startsWith('+7 ')) {
                e.preventDefault();
            }
        });

        setTimeout(() => {
            phoneInput.focus();
            phoneInput.setSelectionRange(phoneInput.value.length, phoneInput.value.length);
        }, 100);
    }

    /**
     * Обработка ручного ввода номера телефона
     */
    submitManualPhone() {
        const phoneInput = document.getElementById('manual-phone-input');
        if (phoneInput && phoneInput.value.trim()) {
            const phone = phoneInput.value.trim();
            console.log('📱 Manual phone input:', phone);

            const validationResult = this.validatePhoneNumber(phone);
            if (!validationResult.isValid) {
                alert(`❌ ${validationResult.error}`);
                return;
            }

            const formattedPhone = validationResult.formatted;
            console.log('✅ Phone validated:', formattedPhone);

            this.userData = this.userData || {};
            this.userData.phone = formattedPhone;

            this.displayExistingPhoneNumber(formattedPhone);
            this.updateSubmitButtonState();

            // Сохраняем на сервере
            this.savePhoneToProfile(formattedPhone);
        }
    }

    /**
     * Сохранение телефона в профиле
     */
    async savePhoneToProfile(phone) {
        try {
            // Переавторизуемся с номером телефона для сохранения
            if (this.max?.initData) {
                await this.api.authenticateWebApp(this.max.initData, phone);
            }
        } catch (error) {
            console.warn('Could not save phone to profile:', error);
        }
    }

    /**
     * Валидация номера телефона
     */
    validatePhoneNumber(phone) {
        if (!phone || !phone.trim()) {
            return {
                isValid: false,
                error: 'Номер телефона не может быть пустым'
            };
        }

        const cleaned = phone.replace(/[^0-9+]/g, '');
        const digitsOnly = cleaned.replace(/[^0-9]/g, '');

        console.log('📱 Validating phone:', phone, '-> digits:', digitsOnly);

        let formatted = null;

        if (digitsOnly.startsWith('7') && digitsOnly.length === 11) {
            formatted = '+' + digitsOnly;
        } else if (digitsOnly.startsWith('8') && digitsOnly.length === 11) {
            formatted = '+7' + digitsOnly.substring(1);
        } else if (digitsOnly.length === 10) {
            formatted = '+7' + digitsOnly;
        } else if (cleaned.startsWith('+7') && digitsOnly.length === 11) {
            formatted = '+' + digitsOnly;
        } else {
            return {
                isValid: false,
                error: `Неправильный формат номера. Введено цифр: ${digitsOnly.length}`
            };
        }

        if (!formatted || formatted.length !== 12 || !formatted.startsWith('+7')) {
            return {
                isValid: false,
                error: 'Ошибка форматирования номера телефона'
            };
        }

        return {
            isValid: true,
            formatted: formatted
        };
    }

    /**
     * Обновление состояния кнопки оформления заказа
     */
    updateSubmitButtonState() {
        const submitButton = document.getElementById('submit-order');
        if (!submitButton) return;

        const hasName = this.userData?.name && this.userData.name !== 'Данные не загружены';
        const hasPhone = this.userData?.phone && this.userData.phone.length > 0;
        const hasCart = this.cart?.items && this.cart.items.length > 0;
        const totalAmount = (this.cart?.totalAmount || 0) + (this.deliveryCost || 0);

        if (hasName && hasPhone && hasCart) {
            submitButton.disabled = false;
            submitButton.textContent = `Оформить заказ ₽${totalAmount}`;
            submitButton.style.opacity = '1';
        } else {
            submitButton.disabled = true;
            if (!hasPhone) {
                submitButton.textContent = 'Требуется номер телефона';
            } else if (!hasName) {
                submitButton.textContent = 'Требуется авторизация';
            } else if (!hasCart) {
                submitButton.textContent = 'Корзина пуста';
            }
            submitButton.style.opacity = '0.6';
        }
    }

    /**
     * Настройка UI
     */
    setupUI() {
        this.renderOrderItems();

        document.getElementById('back-button')?.addEventListener('click', () => {
            window.history.back();
        });

        document.querySelectorAll('input[name="deliveryMethod"]').forEach(input => {
            input.addEventListener('change', (e) => {
                this.handleDeliveryMethodChange(e.target.value);
            });
        });

        document.querySelectorAll('input[name="paymentMethod"]').forEach(input => {
            input.addEventListener('change', (e) => {
                this.handlePaymentMethodChange(e.target.value);
            });
        });

        const addressInput = document.getElementById('address-input');
        if (addressInput) {
            addressInput.addEventListener('input', this.debounce((e) => {
                this.handleAddressInput(e.target.value);
            }, 500));
        }

        document.getElementById('submit-order')?.addEventListener('click', () => {
            this.submitOrder();
        });

        document.getElementById('retry-button')?.addEventListener('click', () => {
            window.location.reload();
        });

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

        if (!this.cart.items || this.cart.items.length === 0) {
            container.innerHTML = '<div class="empty-cart">Корзина пуста</div>';
            return;
        }

        this.cart.items.forEach(item => {
            const itemElement = document.createElement('div');
            itemElement.className = 'order-item';
            itemElement.innerHTML = `
                <img src="${item.imageUrl || '/static/images/products/pizza_4_chees.png'}"
                     alt="${item.name || 'Товар'}"
                     class="order-item-image">
                <div class="order-item-info">
                    <div class="order-item-title">${item.name || 'Товар'}</div>
                    <div class="order-item-details">${item.quantity || 1} шт. × ₽${item.price || 0}</div>
                </div>
                <div class="order-item-price">₽${item.subtotal || (item.price * item.quantity) || 0}</div>
            `;
            container.appendChild(itemElement);
        });

        setTimeout(() => {
            this.updateTotals();
        }, 50);
    }

    /**
     * Обработка изменения способа доставки
     */
    async handleDeliveryMethodChange(method) {
        this.deliveryMethod = method;
        const addressSection = document.getElementById('address-section');

        if (method === 'DELIVERY') {
            addressSection.style.display = 'block';

            const addressInput = document.getElementById('address-input');
            const currentAddress = addressInput?.value?.trim();

            if (currentAddress && currentAddress.length >= 3) {
                await this.calculateDeliveryCost(currentAddress);
            } else {
                this.deliveryCost = 0;
            }
        } else {
            addressSection.style.display = 'none';
            this.deliveryCost = 0;
            this.address = '';
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

        if (address.length < 3) {
            if (this.deliveryMethod === 'DELIVERY') {
                this.deliveryCost = 0;
                this.updateDeliveryPrice();
                this.updateTotals();
            }
            return;
        }

        try {
            if (this.deliveryMethod === 'DELIVERY') {
                await this.calculateDeliveryCost(address);
            }
        } catch (error) {
            console.warn('Ошибка при обработке адреса:', error);
            this.deliveryCost = 0;
            this.updateDeliveryPrice();
            this.updateTotals();
        }
    }

    /**
     * Расчет стоимости доставки
     */
    async calculateDeliveryCost(address) {
        try {
            console.log('🚗 Calculating delivery cost for:', address);

            const data = await this.api.calculateDeliveryCost(address, this.cart.totalAmount);

            if (data && data.deliveryAvailable === true) {
                this.deliveryCost = data.deliveryCost || 0;
                console.log(`✅ Delivery cost: ${this.deliveryCost}₽`);
            } else if (data && data.deliveryAvailable === false) {
                this.deliveryCost = 0;
                console.warn('⚠️ Delivery not available for this address');
            } else {
                this.deliveryCost = 250; // Fallback
            }
        } catch (error) {
            console.error('❌ Error calculating delivery cost:', error);
            this.deliveryCost = 250;
        }

        this.updateDeliveryPrice();
        this.updateTotals();
    }

    /**
     * Обновление отображения стоимости доставки
     */
    updateDeliveryPrice() {
        const priceElement = document.getElementById('delivery-cost');
        if (priceElement) {
            if (this.deliveryCost > 0) {
                priceElement.textContent = `₽${this.deliveryCost}`;
                priceElement.style.color = '';
            } else {
                priceElement.textContent = 'Бесплатно';
                priceElement.style.color = 'var(--tg-theme-link-color, #007aff)';
            }
        }
    }

    /**
     * Обновление итоговых сумм
     */
    updateTotals() {
        const itemsTotal = this.cart.totalAmount || 0;
        const totalAmount = itemsTotal + (this.deliveryCost || 0);

        const itemsTotalEl = document.getElementById('items-total');
        if (itemsTotalEl) {
            itemsTotalEl.textContent = `₽${itemsTotal}`;
        }

        const deliveryCostEl = document.getElementById('delivery-cost');
        if (deliveryCostEl) {
            deliveryCostEl.textContent = `₽${this.deliveryCost || 0}`;
        }

        const totalAmountEl = document.getElementById('total-amount');
        if (totalAmountEl) {
            totalAmountEl.textContent = `₽${totalAmount}`;
        }

        const finalTotalEl = document.getElementById('final-total');
        if (finalTotalEl) {
            finalTotalEl.textContent = `₽${totalAmount}`;
        }

        this.updateSubmitButtonState();
    }

    /**
     * Оформление заказа
     */
    async submitOrder() {
        try {
            if (!this.cart.items || this.cart.items.length === 0) {
                this.showError('Корзина пуста');
                return;
            }

            if (!this.userData?.name) {
                this.showError('Данные пользователя не загружены');
                return;
            }

            if (!this.userData.phone) {
                this.showError('Укажите номер телефона');
                return;
            }

            if (this.deliveryMethod === 'DELIVERY' && !this.address) {
                this.showError('Укажите адрес доставки');
                return;
            }

            const submitButton = document.getElementById('submit-order');
            submitButton.disabled = true;
            submitButton.textContent = 'Оформляем заказ...';

            const orderData = {
                contactName: this.userData.name,
                contactPhone: this.userData.phone,
                comment: document.getElementById('order-comment')?.value.trim() || '',
                paymentMethod: this.paymentMethod,
                items: this.cart.items.map(item => ({
                    productId: item.productId,
                    quantity: item.quantity,
                    price: item.price
                }))
            };

            if (this.deliveryMethod === 'DELIVERY') {
                orderData.deliveryAddress = this.address;
                orderData.deliveryType = 'Доставка курьером';
                orderData.deliveryCost = this.deliveryCost;
            } else {
                orderData.deliveryLocationId = 1;
                orderData.deliveryType = 'Самовывоз';
                orderData.deliveryCost = 0;
            }

            console.log('Creating order with data:', orderData);

            // Синхронизируем корзину с бэкендом
            await this.api.clearCart();
            for (const item of this.cart.items) {
                await this.api.addToCart(item.productId, item.quantity);
            }

            // Создаем заказ
            const order = await this.api.createOrder(orderData);

            if (this.paymentMethod === 'SBP') {
                console.log('💳 Creating SBP payment for order:', order.id);
                const payment = await this.api.createPayment(order.id, 'SBP');

                if (payment && (payment.confirmation?.confirmation_url || payment.confirmationUrl)) {
                    const paymentUrl = payment.confirmation?.confirmation_url || payment.confirmationUrl;

                    trackPurchase(order, this.cart.items);
                    this.clearCart();

                    // Открываем страницу оплаты (MAX использует openLink)
                    if (this.max?.openLink) {
                        this.max.openLink(paymentUrl);
                    } else {
                        window.open(paymentUrl, '_blank');
                    }

                    alert('Заказ создан! Переходим к оплате...');
                } else {
                    throw new Error('Ошибка: некорректный ответ от платежной системы');
                }
            } else {
                trackPurchase(order, this.cart.items);
                this.clearCart();
                alert('Заказ успешно оформлен!');

                setTimeout(() => {
                    window.location.href = 'menu.html';
                }, 2000);
            }

        } catch (error) {
            console.error('❌ Order submission failed:', error);
            this.showError('Ошибка оформления заказа: ' + error.message);

            const submitButton = document.getElementById('submit-order');
            submitButton.disabled = false;
            this.updateTotals();
        }
    }

    /**
     * Очистка корзины
     */
    clearCart() {
        this.cart = { items: [], totalAmount: 0 };
        localStorage.removeItem('pizzanat_max_cart');
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

        setTimeout(() => {
            this.updateSubmitButtonState();
        }, 100);
    }

    /**
     * Показать приложение
     */
    showApp() {
        document.getElementById('loading-screen').style.display = 'none';
        document.getElementById('app').style.display = 'block';

        setTimeout(() => {
            this.updateTotals();
        }, 100);
    }

    /**
     * Показать ошибку
     */
    showError(message) {
        alert(message);
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

// Глобальный доступ
window.PizzaNatMaxCheckoutApp = PizzaNatMaxCheckoutApp;
