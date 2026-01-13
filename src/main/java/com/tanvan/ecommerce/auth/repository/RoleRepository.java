package com.tanvan.ecommerce.auth.repository;

import com.tanvan.ecommerce.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

}
