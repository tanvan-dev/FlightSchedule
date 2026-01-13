package com.tanvan.ecommerce.auth.dto;

import lombok.Data;

@Data
public class UserRegister {
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String address;
    private String avatarUrl;
}
