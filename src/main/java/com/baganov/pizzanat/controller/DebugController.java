package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.repository.RoleRepository;
import com.baganov.pizzanat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для отладки работы приложения
 */
@Slf4j
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * Проверка доступности API
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        log.info("Запрос статуса системы");

        Map<String, Object> status = new HashMap<>();
        status.put("status", "up");
        status.put("usersCount", userRepository.count());
        status.put("rolesCount", roleRepository.count());
        status.put("roles", roleRepository.findAll());

        return ResponseEntity.ok(status);
    }

    /**
     * Информация о текущей аутентификации
     */
    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authInfo() {
        log.info("Запрос информации об аутентификации");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> authInfo = new HashMap<>();

        if (auth != null) {
            authInfo.put("authenticated", auth.isAuthenticated());
            authInfo.put("principal", auth.getPrincipal());
            authInfo.put("authorities", auth.getAuthorities());
            authInfo.put("details", auth.getDetails());
            authInfo.put("name", auth.getName());
        } else {
            authInfo.put("authenticated", false);
            authInfo.put("error", "No authentication information available");
        }

        return ResponseEntity.ok(authInfo);
    }
}