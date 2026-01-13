//package com.tanvan.ecommerce.config;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//
//@Configuration
//@RequiredArgsConstructor
//public class SecurityConfig {
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
//    }
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(csrf -> csrf.disable()) // Tắt CSRF cho API
//                .sessionManagement(session ->
//                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Không dùng session
//                .authorizeHttpRequests(auth -> auth
//                        // Cho phép các endpoint auth không cần token
//                        .requestMatchers("/api/auth/register").permitAll()
//                        .requestMatchers("/api/auth/login").permitAll()
//                        .requestMatchers("/api/auth/refresh").permitAll()
//                        .requestMatchers("/api/**").permitAll() // Nếu có recipe API
//                        .anyRequest().authenticated() // Các endpoint khác cần auth
//                );
//
//        return http.build();
//    }
//}
