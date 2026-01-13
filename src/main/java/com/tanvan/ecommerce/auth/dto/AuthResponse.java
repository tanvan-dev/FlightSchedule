package com.tanvan.ecommerce.auth.dto;

import com.tanvan.ecommerce.auth.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UserInformation user;
}
