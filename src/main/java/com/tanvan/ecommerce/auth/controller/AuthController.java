package com.tanvan.ecommerce.auth.controller;

import com.tanvan.ecommerce.auth.dto.AuthResponse;
import com.tanvan.ecommerce.auth.dto.LoginRequest;
import com.tanvan.ecommerce.auth.dto.UserRegister;
import com.tanvan.ecommerce.auth.entity.User;
import com.tanvan.ecommerce.auth.service.AuthService;
import com.tanvan.ecommerce.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public Optional<User> register(@RequestBody UserRegister userRegister) {
        return authService.registerUser(userRegister);
    }

    @PostMapping("/login")
    public Optional<AuthResponse> login(@RequestBody LoginRequest loginRequest) {
        return authService.loginUser(loginRequest);
        
    }

    @PostMapping("/refresh")
    public Optional<AuthResponse> refresh(HttpServletRequest request) {
        // Lấy refresh token từ header Authorization
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String refreshToken = authHeader.substring(7); // Bỏ "Bearer "

        return authService.refresh(refreshToken);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        String userId = jwtUtil.extractUserId(token);
        authService.logout(userId);
    }
}
