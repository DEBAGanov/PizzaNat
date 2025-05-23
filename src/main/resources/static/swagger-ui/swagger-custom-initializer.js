window.onload = function() {
  // Добавление нашего кастомного CSS
  const customCssLink = document.createElement('link');
  customCssLink.rel = 'stylesheet';
  customCssLink.type = 'text/css';
  customCssLink.href = '/swagger-ui/swagger-custom.css';
  document.head.appendChild(customCssLink);

  // Инициализация Swagger UI
  window.ui = SwaggerUIBundle({
    url: "/v3/api-docs",
    dom_id: '#swagger-ui',
    deepLinking: true,
    displayOperationId: true,
    defaultModelsExpandDepth: 1,
    defaultModelExpandDepth: 1,
    defaultModelRendering: 'model',
    displayRequestDuration: true,
    docExpansion: 'none',
    filter: true,
    showExtensions: true,
    showCommonExtensions: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: "StandaloneLayout",
    configUrl: "/v3/api-docs/swagger-config",
    validatorUrl: "",
    oauth2RedirectUrl: window.location.origin + "/swagger-ui/oauth2-redirect.html",
    
    // Настройки для запоминания состояния авторизации
    persistAuthorization: true,

    // Добавляем дополнительную информацию о запросах и ответах
    onComplete: function() {
      // Добавляем информацию о версии API внизу страницы
      const footer = document.createElement('div');
      footer.className = 'swagger-ui-footer';
      footer.innerHTML = '<div class="footer-container" style="text-align: center; margin-top: 30px; padding: 20px; border-top: 1px solid #ddd;">' +
                         '<p>&copy; 2025 PizzaNat. All rights reserved.</p>' +
                         '<p>API Version: 1.0.0 - <a href="mailto:support@pizzanat.com">support@pizzanat.com</a></p>' +
                         '</div>';
      document.querySelector('.swagger-ui').appendChild(footer);
    }
  });
}; 