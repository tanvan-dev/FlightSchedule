package com.tanvan.ecommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipes") // Tên bảng trong database
@Data // Lombok tự động tạo getter/setter
@AllArgsConstructor
@NoArgsConstructor
public class Recipe {

    @Id
    @Column(name = "recipe_id")
    private Long id; // Dùng id từ API làm primary key

    @Column(nullable = false)
    private String title;

    @Column(length = 500) // Giới hạn độ dài URL ảnh
    private String image;

    @Column(name = "image_type")
    private String imageType;
}
