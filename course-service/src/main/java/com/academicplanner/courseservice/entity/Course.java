package com.academicplanner.courseservice.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "courses")
@Schema(description = "Course entity representing an academic course")
@Data
@NoArgsConstructor
public class Course implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Unique identifier for the course", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Course code is required")
    @Schema(description = "Unique course code", example = "CS101", required = true)
    private String courseCode;

    @NotBlank(message = "Title is required")
    @Schema(description = "Course title", example = "Introduction to Programming", required = true)
    private String title;

    @Column(length = 1000)
    @Schema(description = "Detailed course description", example = "Basic programming concepts using Java")
    private String description;

    @NotBlank(message = "Department is required")
    @Schema(description = "Academic department", example = "Computer Science", required = true)
    private String department;

    @NotNull(message = "Credits is required")
    @Positive(message = "Credits must be positive")
    @Schema(description = "Number of credit hours", example = "3", required = true)
    private Integer credits;

    // Constructor
    public Course(String courseCode, String title, String description, String department, Integer credits) {
        this.courseCode = courseCode;
        this.title = title;
        this.description = description;
        this.department = department;
        this.credits = credits;
    }
}