package com.baganov.pizzanat.config;

import com.baganov.pizzanat.entity.OrderStatus;
import com.baganov.pizzanat.entity.Role;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.repository.OrderStatusRepository;
import com.baganov.pizzanat.repository.RoleRepository;
import com.baganov.pizzanat.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Компонент для инициализации тестовых данных при запуске приложения
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("🚀 DataInitializer запущен! Начинаю инициализацию данных...");
        try {
            initializeRoles();
            initializeOrderStatuses();
            createTestUsers();
            log.info("✅ DataInitializer завершен успешно!");
        } catch (Exception e) {
            log.error("❌ Ошибка в DataInitializer: {}", e.getMessage(), e);
        }
    }

    private void initializeRoles() {
        if (roleRepository.count() == 0) {
            log.info("Инициализация ролей");
            Role userRole = new Role();
            userRole.setName("ROLE_USER");
            roleRepository.save(userRole);

            Role adminRole = new Role();
            adminRole.setName("ROLE_ADMIN");
            roleRepository.save(adminRole);
            log.info("Роли успешно созданы");
        }
    }

    private void initializeOrderStatuses() {
        log.info("Инициализация статусов заказов");

        // Создаем все необходимые статусы заказов
        createOrderStatus("CREATED", "Заказ создан");
        createOrderStatus("CONFIRMED", "Заказ подтвержден");
        createOrderStatus("PREPARING", "Заказ готовится");
        createOrderStatus("READY", "Заказ готов");
        createOrderStatus("DELIVERING", "Заказ доставляется");
        createOrderStatus("DELIVERED", "Заказ доставлен");
        createOrderStatus("CANCELLED", "Заказ отменен");

        log.info("Статусы заказов успешно созданы");
    }

    private void createOrderStatus(String name, String description) {
        if (orderStatusRepository.findByName(name).isEmpty()) {
            OrderStatus status = new OrderStatus();
            status.setName(name);
            status.setDescription(description);
            status.setActive(true);
            orderStatusRepository.save(status);
            log.info("Создан статус заказа: {} - {}", name, description);
        }
    }

    private void createTestUsers() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            log.info("Создаю тестового пользователя 'admin'");

            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseThrow(() -> new IllegalStateException("Роль ROLE_ADMIN не найдена"));

            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@example.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFirstName("Администратор");
            admin.setLastName("Системы");
            admin.setPhone("+79001234567");
            admin.setActive(true);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            admin.setRoles(roles);

            userRepository.save(admin);
            log.info("Тестовый пользователь 'admin' создан успешно");
        }

        if (userRepository.findByUsername("user").isEmpty()) {
            log.info("Создаю тестового пользователя 'user'");

            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("Роль ROLE_USER не найдена"));

            User user = new User();
            user.setUsername("user");
            user.setEmail("user@example.com");
            user.setPassword(passwordEncoder.encode("password"));
            user.setFirstName("Обычный");
            user.setLastName("Пользователь");
            user.setPhone("+79007654321");
            user.setActive(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());

            Set<Role> roles = new HashSet<>();
            roles.add(userRole);
            user.setRoles(roles);

            userRepository.save(user);
            log.info("Тестовый пользователь 'user' создан успешно");
        }
    }
}