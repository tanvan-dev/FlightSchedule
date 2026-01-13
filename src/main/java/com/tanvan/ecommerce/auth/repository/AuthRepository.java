package com.tanvan.ecommerce.auth.repository;

import com.tanvan.ecommerce.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface AuthRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
}
