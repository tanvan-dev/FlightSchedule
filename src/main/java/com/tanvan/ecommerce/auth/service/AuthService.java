package com.tanvan.ecommerce.auth.service;

import com.tanvan.ecommerce.auth.dto.AuthResponse;
import com.tanvan.ecommerce.auth.dto.LoginRequest;
import com.tanvan.ecommerce.auth.dto.UserInformation;
import com.tanvan.ecommerce.auth.dto.UserRegister;
import com.tanvan.ecommerce.auth.entity.RefreshToken;
import com.tanvan.ecommerce.auth.entity.User;
import com.tanvan.ecommerce.auth.repository.AuthRepository;
import com.tanvan.ecommerce.auth.repository.RefreshTokenRepository;
import com.tanvan.ecommerce.auth.repository.RoleRepository;
import com.tanvan.ecommerce.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;


    public AuthService(AuthRepository authRepository, RoleRepository roleRepository, RefreshTokenRepository refreshTokenRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findByUsername(String username) {
        return authRepository.findByUsername(username);
    }

    public Optional<User> registerUser(UserRegister register) {
        if (authRepository.findByUsername(register.getUsername()).isPresent()) {
            return Optional.empty(); // Username already exists
        }
        User newUser = User.builder()
                .username(register.getUsername())
                .password(passwordEncoder.encode(register.getPassword()))
                .email(register.getEmail())
                .fullName(register.getFullName())
                .phoneNumber(register.getPhoneNumber())
                .address(register.getAddress())
                .avatarUrl(register.getAvatarUrl())
                .isActive(true)
                .build();
        User savedUser = authRepository.save(newUser);
        return Optional.of(savedUser);
    }

    // ---------------------- LOGIN ----------------------
    public Optional<AuthResponse> loginUser(LoginRequest req) {
        User user = authRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String access = jwtUtil.generateAccessToken(user.getId());
        String refresh = jwtUtil.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refresh)
                .user(user)
                .expiresAt(jwtUtil.getRefreshTokenExpiration())
                .build();
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .user(convertUserToUserInformation(user))
                .build();
        refreshTokenRepository.save(refreshToken);
        return Optional.of(authResponse);
    }

    public Optional<AuthResponse> refresh(String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (storedToken.getExpiresAt().isBefore(java.time.Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new RuntimeException("Refresh token expired");
        }

        String userId = storedToken.getUser().getId();
        String newAccessToken = jwtUtil.generateAccessToken(userId);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        storedToken.setToken(newRefreshToken);
        storedToken.setExpiresAt(jwtUtil.getRefreshTokenExpiration());
        refreshTokenRepository.save(storedToken);

        return Optional.of(AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build());
    }

    public void logout(String userId) {
        Optional<User> userOpt = authRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            refreshTokenRepository.findAll().stream()
                    .filter(token -> token.getUser().getId().equals(user.getId()))
                    .forEach(refreshTokenRepository::delete);
        }
    }

    public UserInformation convertUserToUserInformation(User user) {
        UserInformation userInfo = new UserInformation();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setFullName(user.getFullName());
        userInfo.setPhoneNumber(user.getPhoneNumber());
        userInfo.setAddress(user.getAddress());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        return userInfo;
    }
}
