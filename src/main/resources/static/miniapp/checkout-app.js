/**
 * PizzaNat Mini App - Checkout Page
 * Order processing with delivery and payment selection
 */

class PizzaNatCheckoutApp {
    constructor() {
        this.tg = window.Telegram?.WebApp;
        this.api = null; // Будет инициализировано в init()
        this.cart = { items: [], totalAmount: 0 };
        this.deliveryMethod = 'DELIVERY'; // Default to delivery
        this.paymentMethod = 'SBP'; // Default to SBP
        this.deliveryCost = 200; // Default delivery cost
        this.address = '';
        this.authToken = null;
        this.pendingOrderSubmission = false; // Флаг для отложенного оформления заказа
        
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
            // Инициализация API
            if (!this.api) {
                if (window.PizzaAPI) {
                    this.api = window.PizzaAPI;
                } else {
                    // Создаем новый экземпляр API если глобальный не найден
                    this.api = new PizzaAPI();
                }
                console.log('📡 API initialized:', this.api.baseURL);
            }
            
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
                        subtotal: 500,
                        imageUrl: '/static/images/products/pizza_4_chees.png'
                    }
                ];
                this.cart.totalAmount = 500;
                this.saveCartToStorage();
            }
            
            // Настройка UI
            this.setupUI();
            
            // Загрузка данных
            await this.loadUserData();
            
            // Загрузка последнего адреса доставки
            await this.loadLastDeliveryAddress();
            
            // Показываем приложение
            this.showApp();
            
            console.log('✅ Checkout initialized successfully');
            
        } catch (error) {
            console.error('❌ Checkout initialization failed:', error);
            console.error('❌ Error stack:', error.stack);
            this.showError(`Ошибка загрузки формы заказа: ${error.message}`);
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
        console.log('🔐 Starting authentication...');
        console.log('📱 Telegram WebApp available:', !!this.tg);
        console.log('📋 Telegram initData available:', !!this.tg?.initData);
        
        if (!this.tg?.initData) {
            console.warn('⚠️ No Telegram initData available - using demo mode');
            return;
        }

        if (!this.api) {
            console.error('❌ API not initialized for authentication');
            return;
        }

        console.log('🔐 Authenticating user with initData...');

        try {
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
     * Обработка полученной контактной информации
     */
    handleContactReceived(data) {
        console.log('📞 Получена контактная информация:', data);

        if (data.status === 'sent' && data.contact) {
            // Обновляем данные пользователя
            const contactName = data.contact.first_name || this.userData?.name || 'Пользователь';
            const contactPhone = data.contact.phone_number || '';

            // Сохраняем предыдущее имя если оно есть
            const currentName = this.userData?.name || contactName;

            this.userData = {
                name: currentName,
                phone: contactPhone
            };

            // Обновляем отображение
            const userNameEl = document.getElementById('user-name');
            const userPhoneEl = document.getElementById('user-phone');
            
            if (userNameEl && !userNameEl.textContent.includes('Данные не загружены')) {
                userNameEl.textContent = currentName;
                userNameEl.style.color = '';
            }
            
            if (userPhoneEl) {
                userPhoneEl.textContent = contactPhone;
                userPhoneEl.style.color = '';
            }

            // Обновляем состояние кнопки
            this.updateSubmitButtonState();

            console.log('✅ Контактная информация обновлена:', { name: currentName, phone: contactPhone });
            
            // Показываем уведомление об успешном получении контакта
            if (this.tg?.showPopup) {
                this.tg.showPopup({
                    title: 'Контакт получен',
                    message: 'Теперь вы можете оформить заказ',
                    buttons: [{ type: 'ok' }]
                });
            }
            
            // Если у нас есть отложенный заказ, продолжаем его оформление
            if (this.pendingOrderSubmission) {
                console.log('🚀 Продолжаем оформление заказа с полученным контактом');
                this.pendingOrderSubmission = false;
                setTimeout(() => {
                    this.submitOrder();
                }, 500); // Небольшая задержка для лучшего UX
            }
        } else {
            console.warn('⚠️ Пользователь отменил предоставление контакта');
            // Не показываем ошибку, просто оставляем кнопку неактивной
            if (this.pendingOrderSubmission) {
                this.pendingOrderSubmission = false;
            }
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
                    userPhoneEl.textContent = phoneNumber || 'Требуется номер телефона';
                    if (!phoneNumber) {
                        userPhoneEl.style.color = 'var(--tg-theme-destructive-text-color, #ff6b6b)';
                    }
                }
                
                // Сохраняем данные для отправки заказа
                this.userData = {
                    name: fullName,
                    phone: phoneNumber
                };
                
                // Обновляем состояние кнопки оформления заказа
                this.updateSubmitButtonState();
                
                console.log('✅ User data loaded successfully', { hasPhone: !!phoneNumber });
                
                // Если нет телефона, сразу предлагаем поделиться контактом
                if (!phoneNumber && this.tg && this.tg.requestContact) {
                    console.log('📱 Номер телефона отсутствует, запрашиваем контакт...');
                    setTimeout(() => {
                        this.tg.requestContact();
                    }, 1000); // Небольшая задержка для улучшения UX
                }
            } else {
                console.warn('⚠️ No user profile found');
                this.handleMissingUserData();
            }
            
        } catch (error) {
            console.error('❌ Failed to load user data:', error.message, error);
            console.log('🔧 Trying to work without auth...');
            this.handleMissingUserData();
        }
    }
    
    /**
     * Обновление состояния кнопки оформления заказа
     */
    updateSubmitButtonState() {
        const submitButton = document.getElementById('submit-order');
        if (!submitButton) {
            console.warn('⚠️ Submit button not found in DOM');
            return;
        }

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
     * Обработка отсутствующих данных пользователя
     */
    handleMissingUserData() {
        // Используем данные из Telegram если возможно
        const telegramUser = this.tg?.initDataUnsafe?.user;
        const fallbackName = telegramUser ? 
            [telegramUser.first_name, telegramUser.last_name].filter(Boolean).join(' ') : 
            'Пользователь Telegram';
        
        // Показываем информацию о пользователе
        const userNameEl = document.getElementById('user-name');
        const userPhoneEl = document.getElementById('user-phone');
        
        if (userNameEl) {
            userNameEl.textContent = fallbackName;
        }
        
        if (userPhoneEl) {
            userPhoneEl.textContent = 'Требуется номер телефона';
            userPhoneEl.style.color = 'var(--tg-theme-destructive-text-color, #ff6b6b)';
        }
        
        // Сохраняем данные для оформления заказа
        this.userData = {
            name: fallbackName,
            phone: ''
        };
        
        // Обновляем состояние кнопки
        this.updateSubmitButtonState();
        
        // Запрашиваем номер телефона
        if (this.tg && this.tg.requestContact) {
            console.log('📱 Requesting phone contact from user...');
            setTimeout(() => {
                this.tg.requestContact();
            }, 1500); // Небольшая задержка
        }
    }

    /**
     * Загрузка последнего адреса доставки
     */
    async loadLastDeliveryAddress() {
        try {
            console.log('📍 Loading last delivery address...');
            const lastDelivery = await this.api.getLastDeliveryAddress();
            
            if (lastDelivery && lastDelivery.address) {
                console.log('✅ Last delivery address found:', lastDelivery);
                
                // Заполняем поле адреса
                const addressInput = document.getElementById('address-input');
                if (addressInput) {
                    addressInput.value = lastDelivery.address;
                    this.address = lastDelivery.address;
                }
                
                // Устанавливаем стоимость доставки
                this.deliveryCost = lastDelivery.deliveryCost;
                
                // Обновляем отображение
                this.updateDeliveryPrice();
                this.updateTotals();
                
                console.log(`📍 Address prefilled: ${lastDelivery.address}, cost: ${lastDelivery.deliveryCost}₽`);
            } else {
                console.log('ℹ️ No previous delivery address found');
            }
        } catch (error) {
            console.warn('⚠️ Could not load last delivery address:', error);
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

        // Повторная попытка при ошибке
        document.getElementById('retry-button')?.addEventListener('click', () => {
            console.log('🔄 Retry button clicked, reloading page...');
            window.location.reload();
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
        if (!container) {
            console.warn('⚠️ Order items container not found in DOM');
            return;
        }

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

        // Обновляем только если DOM полностью готов
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
        const itemsTotal = this.cart.totalAmount || 0;
        const totalAmount = itemsTotal + (this.deliveryCost || 0);

        // Обновляем отображение с проверкой существования элементов
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
        
        // Обновляем состояние кнопки заказа
        this.updateSubmitButtonState();
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
                // Запрашиваем контакт вместо показа ошибки
                if (this.tg && this.tg.requestContact) {
                    console.log('📱 Запрашиваем номер телефона для оформления заказа...');
                    this.pendingOrderSubmission = true; // Флаг для продолжения после получения контакта
                    
                    // Показываем пользователю информационное сообщение
                    if (this.tg.showPopup) {
                        this.tg.showPopup({
                            title: 'Нужен номер телефона',
                            message: 'Для оформления заказа необходимо поделиться номером телефона',
                            buttons: [
                                { type: 'ok', text: 'Поделиться номером' }
                            ]
                        }, () => {
                            this.tg.requestContact();
                        });
                    } else {
                        this.tg.requestContact();
                    }
                    return;
                } else {
                    this.showError('Номер телефона не указан. Пожалуйста, обновите профиль');
                    return;
                }
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
                console.log('💳 Creating SBP payment for order:', order.id);
                const payment = await this.api.createPayment(order.id, 'SBP');
                
                console.log('💳 Payment response:', payment);
                
                // Проверяем структуру ответа ЮКассы
                if (payment && (payment.confirmation?.confirmation_url || payment.confirmationUrl)) {
                    const paymentUrl = payment.confirmation?.confirmation_url || payment.confirmationUrl;
                    
                    // Очищаем корзину
                    this.clearCart();
                    
                    // Открываем страницу оплаты
                    this.tg?.openLink(paymentUrl);
                    
                    // Показываем уведомление
                    this.tg?.showAlert('Заказ создан! Переходим к оплате...');
                    
                } else {
                    console.error('❌ Invalid payment response structure:', payment);
                    throw new Error('Ошибка: получен некорректный ответ от платежной системы');
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
            console.error('❌ Error details:', {
                message: error.message,
                stack: error.stack,
                response: error.response
            });
            
            // Более детальная ошибка
            let errorMessage = 'Ошибка оформления заказа';
            if (error.message.includes('payment')) {
                errorMessage = 'Ошибка создания платежа';
            } else if (error.message.includes('order')) {
                errorMessage = 'Ошибка создания заказа';
            }
            errorMessage += ': ' + error.message;
            
            this.showError(errorMessage);
            
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
        
        // Обновляем состояние кнопки после загрузки корзины
        setTimeout(() => {
            this.updateSubmitButtonState();
        }, 100);
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
     * Показать приложение
     */
    showApp() {
        const loadingScreen = document.getElementById('loading-screen');
        const appContainer = document.getElementById('app');
        
        if (loadingScreen) {
            loadingScreen.style.display = 'none';
        }
        
        if (appContainer) {
            appContainer.style.display = 'block';
        }
        
        // Проверяем что все ключевые элементы присутствуют
        const criticalElements = [
            'order-items',
            'submit-order',
            'user-name',
            'user-phone',
            'items-total',
            'delivery-cost',
            'total-amount',
            'final-total'
        ];
        
        const missingElements = criticalElements.filter(id => !document.getElementById(id));
        if (missingElements.length > 0) {
            console.warn('⚠️ Missing DOM elements:', missingElements);
        }
        
        // Обновляем интерфейс после показа приложения
        setTimeout(() => {
            this.updateTotals();
        }, 100);
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
