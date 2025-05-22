package com.baganov.pizzanat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.issuer:PizzaNat}")
    private String issuer;

    public String extractUsername(String token) {
        log.debug("Извлечение имени пользователя из токена");
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        log.debug("Генерация JWT токена для пользователя: {}", userDetails.getUsername());
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails) {
        log.debug("Генерация JWT токена с дополнительными параметрами для пользователя: {}", userDetails.getUsername());
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration) {
        long currentTime = System.currentTimeMillis();
        Date issuedAt = new Date(currentTime);
        Date expirationDate = new Date(currentTime + expiration);

        log.debug("Построение JWT токена. Пользователь: {}, Дата создания: {}, Дата истечения: {}",
                userDetails.getUsername(), issuedAt, expirationDate);

        try {
            String token = Jwts
                    .builder()
                    .setClaims(extraClaims)
                    .setSubject(userDetails.getUsername())
                    .setIssuedAt(issuedAt)
                    .setExpiration(expirationDate)
                    .setIssuer(issuer)
                    .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                    .compact();
            log.debug("JWT токен успешно создан");
            return token;
        } catch (Exception e) {
            log.error("Ошибка при создании JWT токена: {}", e.getMessage(), e);
            throw e;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        log.debug("Проверка валидности токена для пользователя: {}", userDetails.getUsername());
        final String username = extractUsername(token);
        boolean isValid = (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
        log.debug("Токен {} для пользователя {}", isValid ? "валиден" : "невалиден", userDetails.getUsername());
        return isValid;
    }

    private boolean isTokenExpired(String token) {
        boolean isExpired = extractExpiration(token).before(new Date());
        log.debug("Токен {}", isExpired ? "просрочен" : "действителен");
        return isExpired;
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        try {
            Claims claims = Jwts
                    .parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            log.debug("Успешно извлечены данные из токена");
            return claims;
        } catch (Exception e) {
            log.error("Ошибка при извлечении данных из токена: {}", e.getMessage());
            throw e;
        }
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}