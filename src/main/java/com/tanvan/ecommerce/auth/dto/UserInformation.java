package com.tanvan.ecommerce.auth.dto;

import lombok.Data;

@Data
public class UserInformation {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String address;
    private String avatarUrl;
}
