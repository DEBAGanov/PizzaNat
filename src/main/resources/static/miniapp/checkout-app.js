/**
 * PizzaNat Mini App - Checkout Page
 * Order processing with delivery and payment selection
 */

// Функции для отслеживания электронной коммерции в Яндекс.Метрике
function trackEcommerce(eventType, data) {
    try {
        if (typeof ym !== 'undefined') {
            console.log('📊 YM E-commerce tracking:', eventType, data);
            ym(103585127, 'reachGoal', eventType, data);
            
            // Отправляем данные в dataLayer для электронной коммерции
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
    
    // Яндекс Метрика
    trackEcommerce('begin_checkout', ecommerceData);
    
    // VK пиксель - initiate_checkout тоже важное событие для ретаргетинга
    const productIds = items.map(item => item.productId?.toString());
    trackVKEcommerce('initiate_checkout', {
        value: totalAmount,
        params: {
            product_id: productIds.length === 1 ? productIds[0] : productIds
        }
    });
}

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
        this.contactRequested = false; // Флаг для отслеживания запроса контакта
        
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
        
        // ДИАГНОСТИКА TELEGRAM API
        console.log('🔍 ДИАГНОСТИКА TELEGRAM API:');
        console.log('  - window.Telegram доступен:', !!window.Telegram);
        console.log('  - window.Telegram.WebApp доступен:', !!window.Telegram?.WebApp);
        if (window.Telegram?.WebApp) {
            console.log('  - Telegram WebApp version:', window.Telegram.WebApp.version);
            console.log('  - Telegram WebApp platform:', window.Telegram.WebApp.platform);
            console.log('  - Telegram WebApp methods:', Object.keys(window.Telegram.WebApp).filter(key => typeof window.Telegram.WebApp[key] === 'function'));
        }
        
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
            
            // Загрузка последнего адреса доставки - ОТКЛЮЧЕНО
            // await this.loadLastDeliveryAddress();
            
            // Отслеживание начала оформления заказа
            if (this.cart.items && this.cart.items.length > 0) {
                trackBeginCheckout(this.cart.items, this.cart.totalAmount);
            }
            
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
        console.log('🔍 Telegram API version:', this.tg.version);
        console.log('🔍 Telegram platform:', this.tg.platform);
        console.log('🔍 Telegram WebApp features:', {
            requestContact: typeof this.tg.requestContact,
            requestWriteAccess: typeof this.tg.requestWriteAccess,
            showAlert: typeof this.tg.showAlert,
            showPopup: typeof this.tg.showPopup,
            cloudStorage: typeof this.tg.CloudStorage,
            biometricManager: typeof this.tg.BiometricManager
        });

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
        
        // Подписываемся на события согласно официальной документации
        this.tg.onEvent('themeChanged', () => this.applyTelegramTheme());
        
        // Основное событие запроса контакта (Bot API 6.9+)
        this.tg.onEvent('contactRequested', (data) => {
            console.log('📞 === СОБЫТИЕ contactRequested ПОЛУЧЕНО ===');
            console.log('📞 Данные события:', data);
            console.log('📞 Тип данных:', typeof data);
            console.log('📞 JSON данных:', JSON.stringify(data, null, 2));
            this.handleContactReceived(data);
        });
        
        // Событие записи в ЛС (может быть связано)
        this.tg.onEvent('writeAccessRequested', (data) => {
            console.log('✍️ writeAccessRequested event received:', data);
        });
        
        // Глобальный обработчик для отладки ВСЕХ событий
        const originalOnEvent = this.tg.onEvent;
        this.tg.onEvent = (eventType, handler) => {
            console.log('🔧 Registering event handler for:', eventType);
            return originalOnEvent.call(this.tg, eventType, (data) => {
                console.log(`🎯 Event '${eventType}' fired with data:`, data);
                handler(data);
            });
        };
        
        // Переподписываемся с новым обработчиком
        this.tg.onEvent('contactRequested', (data) => {
            console.log('📞 contactRequested event received:', data);
            this.handleContactReceived(data);
        });
        
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
     * Авторизация пользователя с автоматическим запросом номера телефона
     */
    async authenticate() {
        console.log('🔐 Starting enhanced authentication with automatic phone request...');
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

        // Сразу пробуем расширенную авторизацию без номера телефона для создания токена в telegram_auth_tokens
        try {
            console.log('🔐 Trying enhanced authentication without phone first to create cross-platform token...');
            const response = await this.api.enhancedAuthenticateWebApp(this.tg.initData, null);
            console.log('🔐 Enhanced auth response (without phone):', response);
            
            this.authToken = response.token;
            this.api.setAuthToken(this.authToken);
            
            console.log('✅ Enhanced authentication successful (without phone)');
            
            // Запрашиваем номер телефона для дополнения данных пользователя
            this.requestPhoneForEnhancedAuth();
            return;
            
        } catch (error) {
            console.log('⚠️ Enhanced authentication without phone failed, trying standard auth:', error.message);
            
            // Fallback на стандартную авторизацию
            try {
                console.log('🔐 Trying standard authentication as fallback...');
                const response = await this.api.authenticateWebApp(this.tg.initData);
                console.log('🔐 Standard auth response:', response);
                
                this.authToken = response.token;
                this.api.setAuthToken(this.authToken);
                
                console.log('✅ Standard authentication successful as fallback');
                
                // Запрашиваем номер телефона для полной регистрации
                this.requestPhoneForEnhancedAuth();
                return;
                
            } catch (standardError) {
                console.log('⚠️ Standard authentication also failed:', standardError.message);
            }
        }

        // Если ничего не сработало, все равно запрашиваем номер телефона
        console.log('🔧 Both auth methods failed, requesting phone for full enhanced auth...');
        this.requestPhoneForEnhancedAuth();
    }

    /**
     * Запрос номера телефона для расширенной авторизации
     */
    async requestPhoneForEnhancedAuth() {
        console.log('📱 Requesting phone number for enhanced authentication...');
        
        if (!this.tg?.requestContact) {
            console.warn('⚠️ requestContact not available, using fallback auth');
            return;
        }

        // Подписываемся на событие получения контакта для авторизации
        this.tg.onEvent('contactRequested', async (data) => {
            console.log('📞 Contact received for authentication:', data);
            
            let phoneNumber = null;
            
            // Извлекаем номер телефона из события
            if (data?.contact?.phone_number) {
                phoneNumber = data.contact.phone_number;
            } else if (data?.phone_number) {
                phoneNumber = data.phone_number;
            } else if (this.tg.initDataUnsafe?.user?.phone_number) {
                phoneNumber = this.tg.initDataUnsafe.user.phone_number;
            }

            if (phoneNumber) {
                await this.performEnhancedAuth(phoneNumber);
            } else {
                console.error('❌ Phone number not found in contact data');
                // Fallback to standard auth
                console.log('🔧 Falling back to standard auth without phone...');
            }
        });

        // Автоматически запрашиваем контакт если еще не запрашивали
        if (!this.contactRequested) {
            try {
                console.log('🚀 Requesting contact automatically for authentication...');
                this.tg.requestContact();
                this.contactRequested = true;
                console.log('✅ Contact request sent for authentication');
            } catch (error) {
                console.error('❌ Failed to request contact for authentication:', error);
                console.log('🔧 Continuing with standard auth...');
            }
        }
    }

    /**
     * Выполнение расширенной авторизации с номером телефона
     */
    async performEnhancedAuth(phoneNumber) {
        console.log('🔐 Performing enhanced authentication with phone:', phoneNumber);
        
        try {
            const response = await this.api.enhancedAuthenticateWebApp(this.tg.initData, phoneNumber);
            console.log('🔐 Enhanced auth response:', response);
            
            this.authToken = response.token;
            this.api.setAuthToken(this.authToken);
            
            // Обновляем данные пользователя
            this.userData = {
                name: response.firstName || response.username || 'Пользователь',
                phone: phoneNumber
            };

            // Обновляем UI
            this.updateUserDataDisplay();
            this.updateSubmitButtonState();
            
            if (this.tg?.showAlert) {
                this.tg.showAlert('✅ Вы успешно авторизованы с номером телефона!');
            }
            
            console.log('✅ Enhanced authentication successful with phone');
            
        } catch (error) {
            console.error('❌ Enhanced authentication failed:', error);
            console.log('🔧 Continuing without enhanced auth...');
        }
    }

    /**
     * Обновление отображения данных пользователя в UI
     */
    updateUserDataDisplay() {
        const userNameEl = document.getElementById('user-name');
        
        if (userNameEl && this.userData?.name) {
            userNameEl.textContent = this.userData.name;
            userNameEl.style.color = '';
        }
        
        // Для номера телефона используем новый метод
        if (this.userData?.phone && this.userData.phone.length > 0) {
            this.displayExistingPhoneNumber(this.userData.phone);
        }
    }

    /**
     * Обработка полученной контактной информации согласно Telegram API
     */
    handleContactReceived(data) {
        console.log('📞 Получена контактная информация:', data);
        console.log('📞 Тип данных:', typeof data);
        console.log('📞 Структура данных:', JSON.stringify(data, null, 2));

        // Согласно официальной документации Telegram Bot API 6.9+
        // событие contactRequested возвращает объект со статусом
        let contactData = null;
        let contactReceived = false;
        
        if (data) {
            // Проверяем статус согласно официальной документации
            if (data.status === 'sent' || data.status === 'allowed') {
                console.log('✅ Статус контакта: разрешено');
                contactReceived = true;
                
                // Пытаемся получить данные контакта из разных источников
                if (data.contact) {
                    contactData = data.contact;
                } else if (this.tg?.initDataUnsafe?.user) {
                    // Получаем из initData после разрешения
                    const user = this.tg.initDataUnsafe.user;
                    contactData = {
                        first_name: user.first_name,
                        last_name: user.last_name,
                        phone_number: user.phone_number
                    };
                }
            } else if (data.status === 'cancelled') {
                console.log('❌ Пользователь отменил предоставление контакта');
                this.handleContactCancelled();
                return;
            } else {
                console.log('⚠️ Неизвестный статус контакта:', data.status);
            }
            
            // Fallback: если нет статуса, но есть контактные данные
            if (!contactReceived && (data.contact || (data.first_name && data.phone_number))) {
                console.log('🔄 Fallback: обрабатываем прямые контактные данные');
                contactData = data.contact || data;
                contactReceived = true;
            }
        }

        if (contactData && contactData.phone_number) {
            console.log('✅ Контактные данные найдены:', contactData);
            
            // Обновляем данные пользователя
            const contactName = contactData.first_name || this.userData?.name || 'Пользователь';
            const contactPhone = contactData.phone_number || '';

            // Валидация полученного номера телефона
            const validationResult = this.validatePhoneNumber(contactPhone);
            if (!validationResult.isValid) {
                console.warn('⚠️ Номер телефона от Telegram не прошел валидацию:', contactPhone, validationResult.error);
                if (this.tg?.showAlert) {
                    this.tg.showAlert(`⚠️ Получен некорректный номер телефона: ${validationResult.error}. Введите номер вручную.`);
                }
                this.showManualPhoneInput();
                return;
            }

            const formattedPhone = validationResult.formatted;
            console.log('✅ Номер телефона от Telegram прошел валидацию:', formattedPhone);

            // Сохраняем предыдущее имя если оно есть
            const currentName = this.userData?.name || contactName;

            this.userData = {
                name: currentName,
                phone: formattedPhone
            };

            // Обновляем отображение
            const userNameEl = document.getElementById('user-name');
            
            if (userNameEl && !userNameEl.textContent.includes('Данные не загружены')) {
                userNameEl.textContent = currentName;
                userNameEl.style.color = '';
            }
            
            // Для номера телефона используем новый метод отображения
            this.displayExistingPhoneNumber(formattedPhone);

            // Обновляем состояние кнопки
            this.updateSubmitButtonState();
            
            // Сбрасываем флаг, так как контакт успешно получен
            this.contactRequested = false;
            console.log('🔄 Флаг contactRequested сброшен - контакт успешно получен');

            console.log('✅ Контактная информация обновлена:', { name: currentName, phone: formattedPhone });
            
            // Отправляем данные на сервер для enhanced-auth (сохранение в БД)
            this.performEnhancedAuth(formattedPhone);
            
            // Показываем уведомление об успешном получении контакта
            if (this.tg?.showAlert) {
                this.tg.showAlert(`✅ Контакт получен! Номер: ${formattedPhone}`);
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
            console.warn('⚠️ Контактные данные не найдены или пользователь отменил:', data);
            this.handleContactCancelled();
        }
    }

    /**
     * Обработка отмены предоставления контакта
     */
    handleContactCancelled() {
        console.log('🚫 Обработка отмены контакта');
        
        if (this.pendingOrderSubmission) {
            this.pendingOrderSubmission = false;
        }
        
        // Пробуем альтернативный способ через 2 секунды
        setTimeout(() => {
            this.tryAlternativeContactMethod();
        }, 2000);
    }

    /**
     * Альтернативный способ получения контакта
     */
    tryAlternativeContactMethod() {
        console.log('🔄 Пробуем альтернативный способ получения контакта...');
        
        if (this.tg && this.tg.initDataUnsafe && this.tg.initDataUnsafe.user) {
            const user = this.tg.initDataUnsafe.user;
            console.log('👤 Данные пользователя из initData:', user);
            
            // Проверяем есть ли номер телефона в initData
            if (user.phone_number) {
                console.log('📱 Найден номер телефона в initData:', user.phone_number);
                this.handleContactReceived({
                    first_name: user.first_name,
                    last_name: user.last_name,
                    phone_number: user.phone_number
                });
                return;
            }
        }
        
        // Если ничего не найдено, создаем кнопку для ручного ввода
        this.showManualPhoneInput();
    }

    /**
     * Показать поле для ручного ввода номера телефона (API 7.7)
     * ТОЛЬКО если у нас нет номера телефона от Telegram
     */
    showManualPhoneInput() {
        console.log('📝 Показываем варианты ввода номера (API 7.7)...');
        
        // Проверяем, есть ли у нас уже номер телефона
        if (this.userData && this.userData.phone && this.userData.phone.length > 0) {
            console.log('✅ У нас уже есть номер телефона, не показываем блоки для ввода:', this.userData.phone);
            this.displayExistingPhoneNumber(this.userData.phone);
            return;
        }
        
        const userPhoneEl = document.getElementById('user-phone');
        if (userPhoneEl) {
            userPhoneEl.innerHTML = `
                <div style="margin-bottom: 10px;">
                    <button onclick="window.checkoutApp.requestContactAgain()" 
                            style="width: 100%; padding: 8px 16px; background: #007acc; color: white; border: none; border-radius: 4px; margin-bottom: 8px;">
                        📱 Поделиться контактом еще раз
                    </button>
                </div>
                <div style="text-align: center; margin: 10px 0; color: #666;">или</div>
                <input type="tel" 
                       id="manual-phone-input" 
                       placeholder="+7 XXX XXX XX XX" 
                       value="+7 "
                       style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;"
                       maxlength="18">
                <button onclick="window.checkoutApp.submitManualPhone()" 
                        style="width: 100%; margin-top: 8px; padding: 8px 16px; background: #28a745; color: white; border: none; border-radius: 4px;">
                    ✅ Подтвердить номер
                </button>
            `;
            
            // Добавляем обработчики для автоматического форматирования
            const phoneInput = document.getElementById('manual-phone-input');
            if (phoneInput) {
                this.setupPhoneInputFormatting(phoneInput);
            }
        }
    }

    /**
     * Отображение существующего номера телефона (без кнопок для ввода)
     */
    displayExistingPhoneNumber(phoneNumber) {
        console.log('📞 Отображаем существующий номер телефона:', phoneNumber);
        
        const userPhoneEl = document.getElementById('user-phone');
        if (userPhoneEl) {
            userPhoneEl.innerHTML = '';
            userPhoneEl.textContent = phoneNumber;
            userPhoneEl.style.color = 'var(--tg-theme-text-color, #000000)';
            userPhoneEl.style.fontWeight = 'normal';
        }
    }
    
    /**
     * Настройка автоматического форматирования номера телефона
     */
    setupPhoneInputFormatting(phoneInput) {
        // Обработчик ввода для автоматического форматирования
        phoneInput.addEventListener('input', (e) => {
            let value = e.target.value;
            
            // Убираем все символы кроме цифр и +
            const digits = value.replace(/[^\d+]/g, '');
            
            // Если пользователь удалил +7, восстанавливаем
            if (!digits.startsWith('+7')) {
                if (digits.length === 0) {
                    e.target.value = '+7 ';
                    return;
                }
                // Если начинается с 7, добавляем +
                if (digits.startsWith('7')) {
                    value = '+' + digits;
                } else if (digits.startsWith('8')) {
                    // Заменяем 8 на +7
                    value = '+7' + digits.substring(1);
                } else {
                    // Для любых других цифр добавляем +7 в начало
                    value = '+7' + digits;
                }
            } else {
                value = digits;
            }
            
            // Ограничиваем количество цифр после +7 до 10
            const withoutPrefix = value.substring(2); // Убираем +7
            if (withoutPrefix.length > 10) {
                value = '+7' + withoutPrefix.substring(0, 10);
            }
            
            // Форматируем номер: +7 XXX XXX XX XX
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
            
            // Устанавливаем курсор в конец
            setTimeout(() => {
                e.target.setSelectionRange(e.target.value.length, e.target.value.length);
            }, 0);
        });
        
        // Предотвращаем удаление +7
        phoneInput.addEventListener('keydown', (e) => {
            const cursorPosition = e.target.selectionStart;
            const value = e.target.value;
            
            // Запрещаем удаление +7
            if ((e.key === 'Backspace' || e.key === 'Delete') && cursorPosition <= 3 && value.startsWith('+7 ')) {
                e.preventDefault();
            }
        });
        
        // Фокус на поле после создания
        setTimeout(() => {
            phoneInput.focus();
            phoneInput.setSelectionRange(phoneInput.value.length, phoneInput.value.length);
        }, 100);
    }

    /**
     * Повторный запрос контакта (версия API 7.7 - полная поддержка)
     */
    requestContactAgain() {
        console.log('📱 === НАЧАЛО ДИАГНОСТИКИ ЗАПРОСА КОНТАКТА ===');
        console.log('📱 Повторный запрос контакта...');
        
        // Подробная диагностика
        console.log('🔍 ДИАГНОСТИКА СОСТОЯНИЯ:');
        console.log('  - this.tg доступен:', !!this.tg);
        console.log('  - this.tg.version:', this.tg?.version);
        console.log('  - this.tg.platform:', this.tg?.platform);
        console.log('  - typeof this.tg.requestContact:', typeof this.tg?.requestContact);
        console.log('  - this.contactRequested:', this.contactRequested);
        console.log('  - window.Telegram доступен:', !!window.Telegram);
        console.log('  - window.Telegram.WebApp доступен:', !!window.Telegram?.WebApp);
        console.log('  - window.Telegram.WebApp.version:', window.Telegram?.WebApp?.version);
        
        if (!this.tg) {
            console.error('❌ Telegram WebApp API недоступен');
            this.showManualPhoneInput();
            return;
        }
        
        // Проверяем, не был ли уже запрошен контакт
        if (this.contactRequested) {
            console.log('⚠️ requestContact уже был вызван ранее, но пользователь просит повторно - сбрасываем флаг и пробуем снова');
            this.contactRequested = false; // Сбрасываем флаг для повторного запроса
        }
        
        console.log('🔍 Доступные методы Telegram WebApp:', Object.keys(this.tg).filter(key => typeof this.tg[key] === 'function'));
        
        if (typeof this.tg.requestContact === 'function') {
            console.log('✅ requestContact НАЙДЕН! Выполняем запрос...');
            
            try {
                console.log('🚀 Вызываем this.tg.requestContact()...');
                this.tg.requestContact();
                this.contactRequested = true; // Устанавливаем флаг
                console.log('📞 requestContact() вызван БЕЗ ОШИБОК, флаг установлен');
                
                // Устанавливаем таймаут на случай если событие не придет
                setTimeout(() => {
                    console.log('⏰ ТАЙМАУТ: событие contactRequested не получено за 8 секунд');
                    this.showManualPhoneInput();
                }, 8000);
                
            } catch (error) {
                console.error('❌ ОШИБКА при вызове requestContact:');
                console.error('  - error.name:', error.name);
                console.error('  - error.message:', error.message);
                console.error('  - error.stack:', error.stack);
                
                // Если ошибка "WebAppContactRequested" - это означает что контакт уже был запрошен
                if (error.message === 'WebAppContactRequested') {
                    console.log('ℹ️ Контакт уже был запрошен ранее, устанавливаем флаг');
                    this.contactRequested = true;
                }
                
                this.showManualPhoneInput();
            }
        } else {
            console.warn('⚠️ requestContact НЕ НАЙДЕН или НЕ ФУНКЦИЯ');
            console.warn('  - typeof this.tg.requestContact:', typeof this.tg.requestContact);
            console.warn('  - this.tg.requestContact value:', this.tg.requestContact);
            this.showManualPhoneInput();
        }
        
        console.log('📱 === КОНЕЦ ДИАГНОСТИКИ ЗАПРОСА КОНТАКТА ===');
    }

    /**
     * Обработка ручного ввода номера телефона
     */
    submitManualPhone() {
        const phoneInput = document.getElementById('manual-phone-input');
        if (phoneInput && phoneInput.value.trim()) {
            const phone = phoneInput.value.trim();
            console.log('📱 Ручной ввод номера:', phone);
            
            // Валидация номера телефона
            const validationResult = this.validatePhoneNumber(phone);
            if (!validationResult.isValid) {
                if (this.tg?.showAlert) {
                    this.tg.showAlert(`❌ ${validationResult.error}`);
                } else {
                    alert(`❌ ${validationResult.error}`);
                }
                return;
            }
            
            const formattedPhone = validationResult.formatted;
            console.log('✅ Номер прошел валидацию:', formattedPhone);
            
            this.userData = this.userData || {};
            this.userData.phone = formattedPhone;
            
            // Используем новый метод отображения номера телефона
            this.displayExistingPhoneNumber(formattedPhone);
            
            this.updateSubmitButtonState();
            
            // Сбрасываем флаг, так как контакт введен вручную
            this.contactRequested = false;
            console.log('🔄 Флаг contactRequested сброшен - номер введен вручную');
            
            // Отправляем данные на сервер для enhanced-auth (сохранение в БД)
            this.performEnhancedAuth(formattedPhone);
            
            if (this.tg?.showAlert) {
                this.tg.showAlert(`✅ Номер телефона сохранен: ${formattedPhone}`);
            }
        }
    }

    /**
     * Валидация номера телефона (российский формат)
     */
    validatePhoneNumber(phone) {
        if (!phone || !phone.trim()) {
            return {
                isValid: false,
                error: 'Номер телефона не может быть пустым'
            };
        }

        // Убираем все символы кроме цифр и +
        const cleaned = phone.replace(/[^0-9+]/g, '');
        const digitsOnly = cleaned.replace(/[^0-9]/g, '');

        console.log('📱 Валидация номера:', phone, '-> цифры:', digitsOnly);

        let formatted = null;

        // Проверяем различные форматы российских номеров
        if (digitsOnly.startsWith('7') && digitsOnly.length === 11) {
            // Формат: 79161234567 -> +79161234567
            formatted = '+' + digitsOnly;
        } else if (digitsOnly.startsWith('8') && digitsOnly.length === 11) {
            // Формат: 89161234567 -> +79161234567
            formatted = '+7' + digitsOnly.substring(1);
        } else if (digitsOnly.length === 10) {
            // Формат: 9161234567 -> +79161234567
            formatted = '+7' + digitsOnly;
        } else if (cleaned.startsWith('+7') && digitsOnly.length === 11) {
            // Формат: +79161234567 (уже правильный)
            formatted = '+' + digitsOnly;
        } else {
            return {
                isValid: false,
                error: `Неправильный формат номера. Используйте российский номер (+7, 8 или 10 цифр). Введено цифр: ${digitsOnly.length}`
            };
        }

        // Финальная проверка
        if (!formatted || formatted.length !== 12 || !formatted.startsWith('+7')) {
            return {
                isValid: false,
                error: 'Ошибка форматирования номера телефона'
            };
        }

        console.log('✅ Номер валиден:', formatted);
        return {
            isValid: true,
            formatted: formatted
        };
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
                if (phoneNumber && phoneNumber.length > 0) {
                    // Если есть номер телефона - показываем его как обычный текст
                    this.displayExistingPhoneNumber(phoneNumber);
                } else {
                    // Если номера нет - показываем сообщение
                    const userPhoneEl = document.getElementById('user-phone');
                    if (userPhoneEl) {
                        userPhoneEl.textContent = 'Требуется номер телефона';
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
                
                // Если нет телефона, пробуем разные способы получения
                if (!phoneNumber) {
                    console.log('📱 Номер телефона отсутствует, пробуем разные способы получения...');
                    
                    // Сначала проверяем initData
                    setTimeout(() => {
                        this.tryAlternativeContactMethod();
                    }, 500);
                    
                    // Потом запрашиваем контакт (API 7.7 - полная поддержка)
                    if (this.tg && this.tg.requestContact) {
                        setTimeout(() => {
                            console.log('📱 Запрашиваем контакт через requestContact (API 7.7)...');
                            try {
                                if (!this.contactRequested) {
                                    this.tg.requestContact();
                                    this.contactRequested = true;
                                } else {
                                    console.log('⚠️ requestContact уже был вызван, пропускаем');
                                }
                            } catch (error) {
                                console.warn('⚠️ Ошибка при автозапросе контакта:', error);
                                if (error.message === 'WebAppContactRequested') {
                                    this.contactRequested = true;
                                }
                            }
                        }, 1000);
                    } else {
                        console.log('ℹ️ requestContact недоступен');
                    }
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
        
        // Пробуем получить номер телефона разными способами
        setTimeout(() => {
            this.tryAlternativeContactMethod();
        }, 500);
        
        // Запрашиваем номер телефона через requestContact (API 7.7)
        if (this.tg && this.tg.requestContact) {
            console.log('📱 Requesting phone contact from user (API 7.7)...');
            setTimeout(() => {
                try {
                    if (!this.contactRequested) {
                        this.tg.requestContact();
                        this.contactRequested = true;
                    } else {
                        console.log('⚠️ requestContact уже был вызван, пропускаем');
                    }
                } catch (error) {
                    console.warn('⚠️ Ошибка при запросе контакта:', error);
                    if (error.message === 'WebAppContactRequested') {
                        this.contactRequested = true;
                    }
                }
            }, 1500); // Небольшая задержка
        } else {
            console.log('ℹ️ requestContact недоступен');
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

        // Address input с динамическим обновлением стоимости доставки
        const addressInput = document.getElementById('address-input');
        if (addressInput) {
            // Используем более короткую задержку для более отзывчивого обновления
            addressInput.addEventListener('input', this.debounce((e) => {
                this.handleAddressInput(e.target.value);
            }, 500)); // 500ms задержка для баланса между отзывчивостью и нагрузкой на API
            
            // Также обрабатываем событие потери фокуса для финального расчета
            addressInput.addEventListener('blur', (e) => {
                if (e.target.value.length >= 3) {
                    this.handleAddressInput(e.target.value);
                }
            });
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
     * Обработка изменения способа доставки с поддержкой зональной системы
     */
    async handleDeliveryMethodChange(method) {
        this.deliveryMethod = method;
        const addressSection = document.getElementById('address-section');

        if (method === 'DELIVERY') {
            // Показываем поле адреса
            addressSection.style.display = 'block';
            
            // Если адрес уже введен, пересчитываем стоимость через зональную систему
            const addressInput = document.getElementById('address-input');
            const currentAddress = addressInput?.value?.trim();
            
            if (currentAddress && currentAddress.length >= 3) {
                console.log('🔄 Delivery method changed to DELIVERY, recalculating cost for existing address...');
                await this.calculateDeliveryCost(currentAddress);
            } else {
                // Если адреса нет, сбрасываем стоимость
                this.deliveryCost = 0;
                console.log('🔄 Delivery method changed to DELIVERY, waiting for address input...');
            }
        } else {
            // Самовывоз - скрываем поле адреса и сбрасываем стоимость
            addressSection.style.display = 'none';
            this.deliveryCost = 0;
            this.address = '';
            console.log('🔄 Delivery method changed to PICKUP, delivery cost reset to 0');
        }

        // Обновляем отображение
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
     * Обработка ввода адреса с динамическим обновлением стоимости доставки
     */
    async handleAddressInput(address) {
        this.address = address;

        // Очищаем предыдущую стоимость если адрес слишком короткий
        if (address.length < 3) {
            if (this.deliveryMethod === 'DELIVERY') {
                this.deliveryCost = 0;
                this.updateDeliveryPrice();
                this.updateTotals();
            }
            return;
        }

        try {
            // Динамически рассчитываем стоимость доставки при каждом изменении адреса
            if (this.deliveryMethod === 'DELIVERY') {
                console.log('🔄 Address changed, recalculating delivery cost...');
                await this.calculateDeliveryCost(address);
            }
        } catch (error) {
            console.warn('Ошибка при обработке адреса:', error);
            // При ошибке сбрасываем стоимость доставки
            this.deliveryCost = 0;
            this.updateDeliveryPrice();
            this.updateTotals();
        }
    }

    /**
     * Загрузка подсказок адресов - ОТКЛЮЧЕНО
     * Подсказки адресов убраны согласно требованиям
     */
    async loadAddressSuggestions(query) {
        // Функция отключена - подсказки адресов убраны
        console.log('📍 Address suggestions disabled by design');
        return;
    }

    /**
     * Отображение подсказок адресов - ОТКЛЮЧЕНО
     * Подсказки адресов убраны согласно требованиям
     */
    displayAddressSuggestions(suggestions) {
        // Функция отключена - подсказки адресов убраны
        const container = document.getElementById('address-suggestions');
        if (container) {
            container.innerHTML = '';
            container.style.display = 'none';
        }
    }

    /**
     * Расчет стоимости доставки через зональную систему
     */
    async calculateDeliveryCost(address) {
        try {
            console.log('🚗 Calculating delivery cost for:', address, 'Order amount:', this.cart.totalAmount);
            
            // Используем зональную систему API для расчета стоимости доставки
            const data = await this.api.calculateDeliveryCost(address, this.cart.totalAmount);
            console.log('📊 Delivery zone response:', data);
            
            if (data && data.deliveryAvailable === true) {
                // Зональная система успешно определила зону и стоимость
                this.deliveryCost = data.deliveryCost || 0;
                console.log(`✅ Delivery cost calculated: ${this.deliveryCost}₽ (Zone: ${data.zoneName || 'Unknown'})`);
                
                // Показываем дополнительную информацию о зоне
                if (data.zoneName) {
                    console.log(`📍 Delivery zone: ${data.zoneName}, Time: ${data.estimatedTime || 'N/A'}`);
                }
                
                // Если доставка бесплатная, показываем это
                if (data.isDeliveryFree) {
                    console.log(`🎉 Free delivery! (Order amount ${this.cart.totalAmount}₽ >= ${data.freeDeliveryThreshold}₽)`);
                }
            } else if (data && data.deliveryAvailable === false) {
                // Доставка недоступна для данного адреса
                this.deliveryCost = 0;
                console.warn('⚠️ Delivery not available for this address:', data.message || 'Outside delivery zone');
                
                // Показываем сообщение пользователю
                this.showDeliveryMessage('Доставка недоступна для данного адреса', 'error');
            } else {
                // Неожиданный ответ от API
                console.warn('⚠️ Unexpected delivery API response:', data);
                this.deliveryCost = 250; // Стандартная стоимость как fallback
                this.showDeliveryMessage('Используется стандартная стоимость доставки: 250₽', 'warning');
            }
        } catch (error) {
            console.error('❌ Error calculating delivery cost:', error);
            this.deliveryCost = 250; // Стандартная стоимость как fallback
            this.showDeliveryMessage('Ошибка расчета доставки. Используется стандартная стоимость: 250₽', 'error');
        }

        // Обновляем отображение
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
     * Показать сообщение о доставке пользователю
     */
    showDeliveryMessage(message, type = 'info') {
        console.log(`📋 Delivery message (${type}):`, message);
        
        // Можно показать уведомление через Telegram API
        if (this.tg?.showAlert && type === 'error') {
            this.tg.showAlert(message);
        }
        
        // Или обновить текст в интерфейсе
        const noteElement = document.querySelector('.address-note small');
        if (noteElement && type !== 'info') {
            const originalText = noteElement.textContent;
            noteElement.textContent = message;
            noteElement.style.color = type === 'error' ? '#ff6b6b' : '#ffa500';
            
            // Восстанавливаем оригинальный текст через 5 секунд
            setTimeout(() => {
                noteElement.textContent = originalText;
                noteElement.style.color = '';
            }, 5000);
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
     * Настройка обработки ввода адреса (подсказки убраны)
     */
    setupAddressInput() {
        const addressInput = document.getElementById('address-input');
        
        if (!addressInput) return;
        
        // Обработка фокуса на поле ввода (упрощенная версия без подсказок)
        addressInput.addEventListener('focus', () => {
            document.body.classList.add('keyboard-visible');
            
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
            }, 150);
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
                            title: '🍕 Номер телефона для заказа',
                            message: 'Поделитесь номером телефона для оформления заказа в DIMBO Pizza.\n\nЭто позволит автоматически заполнить данные и связаться с вами по заказу.',
                            buttons: [
                                { type: 'ok', text: 'Поделиться номером' },
                                { type: 'cancel', text: 'Ввести вручную' }
                            ]
                        }, (buttonType) => {
                            if (buttonType === 'ok') {
                                if (!this.contactRequested) {
                                    this.tg.requestContact();
                                    this.contactRequested = true;
                                } else {
                                    console.log('⚠️ requestContact уже был вызван ранее');
                                    this.showManualPhoneInput();
                                }
                            } else {
                                // Пользователь выбрал "Ввести вручную"
                                this.showManualPhoneInput();
                            }
                        });
                    } else {
                        if (!this.contactRequested) {
                            this.tg.requestContact();
                            this.contactRequested = true;
                        } else {
                            console.log('⚠️ requestContact уже был вызван ранее');
                            this.showManualPhoneInput();
                        }
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
                    
                    // Отслеживание покупки в Яндекс.Метрике
                    trackPurchase(order, this.cart.items);
                    
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
                // Отслеживание покупки в Яндекс.Метрике
                trackPurchase(order, this.cart.items);
                
                this.clearCart();
                this.tg?.showAlert('Заказ успешно оформлен! Мы свяжемся с вами для подтверждения.');
                
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
