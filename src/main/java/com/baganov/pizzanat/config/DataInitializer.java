package com.baganov.pizzanat.config;

import com.baganov.pizzanat.entity.Role;
import com.baganov.pizzanat.entity.User;
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
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initializeRoles();
        createTestUsers();
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