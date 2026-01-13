//package com.tanvan.ecommerce.utils;
//
//import io.jsonwebtoken.*;
//import io.jsonwebtoken.security.Keys;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.security.Key;
//import java.time.Instant;
//import java.util.Date;
//import java.util.UUID;
//
//@Component
//public class JwtUtil {
//
//    @Value("${app.jwt.secret}")
//    private String secret;
//
//    @Value("${app.jwt.access-expiration}")
//    private long accessExpiration;
//
//    @Value("${app.jwt.refresh-expiration}")
//    private long refreshExpiration;
//
//    private Key getSigningKey() {
//        return Keys.hmacShaKeyFor(secret.getBytes());
//    }
//
//    public String generateAccessToken(String userId) {
//        return Jwts.builder()
//                .setSubject(userId)
//                .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
//                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
//                .compact();
//    }
//
//    public String generateRefreshToken(String userId) {
//        return Jwts.builder()
//                .setSubject(userId)
//                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
//                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
//                .compact();
//    }
//
//    public String extractUserId(String token) {
//        return Jwts.parserBuilder()
//                .setSigningKey(getSigningKey())
//                .build()
//                .parseClaimsJws(token)
//                .getBody()
//                .getSubject();
//    }
//
//    public boolean isTokenValid(String token) {
//        try {
//            Jwts.parserBuilder()
//                    .setSigningKey(getSigningKey())
//                    .build()
//                    .parseClaimsJws(token);
//            return true;
//        } catch (JwtException e) {
//            return false;
//        }
//    }
//
//    /**
//     * Lấy thời điểm hết hạn của refresh token
//     */
//    public Instant getRefreshTokenExpiration() {
//        return Instant.now().plusMillis(refreshExpiration);
//    }
//
//    /**
//     * Lấy thời điểm hết hạn từ một token
//     */
//    public Instant getExpirationFromToken(String token) {
//        try {
//            Claims claims = Jwts.parserBuilder()
//                    .setSigningKey(getSigningKey())
//                    .build()
//                    .parseClaimsJws(token)
//                    .getBody();
//            return claims.getExpiration().toInstant();
//        } catch (JwtException e) {
//            throw new RuntimeException("Invalid token", e);
//        }
//    }
//}
