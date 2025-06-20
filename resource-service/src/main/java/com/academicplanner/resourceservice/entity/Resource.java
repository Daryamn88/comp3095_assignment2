package com.academicplanner.resourceservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resources")
@Data
@NoArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "URL is required")
    @Pattern(regexp = "^(http|https)://.*", message = "URL must be valid")
    private String url;

    @NotNull(message = "Category is required")
    @Enumerated(EnumType.STRING)
    private ResourceCategory category;

    private String description;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Constructors
    public Resource(String title, String url, ResourceCategory category, String description) {
        this.title = title;
        this.url = url;
        this.category = category;
        this.description = description;
    }
}