package com.academicplanner.assignmentservice.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Document(collection = "assignments")
@Schema(description = "Assignment entity representing a student assignment")
@Data
public class Assignment {

    @Id
    @Schema(description = "Unique identifier for the assignment", example = "507f1f77bcf86cd799439011", accessMode = Schema.AccessMode.READ_ONLY)
    private String assignmentId;

    @NotBlank(message = "Title is required")
    @Schema(description = "Assignment title", example = "Programming Project 1", required = true)
    private String title;

    @NotNull(message = "Due date is required")
    @Schema(description = "Assignment due date and time", example = "2024-12-31T23:59:00", required = true)
    private LocalDateTime dueDate;

    @NotNull(message = "Status is required")
    @Schema(description = "Assignment completion status", example = "PENDING", required = true)
    private AssignmentStatus status;

    @NotBlank(message = "Course code is required")
    @Schema(description = "Associated course code", example = "CS101", required = true)
    private String courseCode;

    @Schema(description = "Detailed assignment description", example = "Build a calculator application using Java")
    private String description;

    @Schema(description = "Assignment creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @Schema(description = "Assignment last update timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;

    // Constructors
    public Assignment() {
        this.status = AssignmentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Assignment(String title, LocalDateTime dueDate, String courseCode, String description) {
        this();
        this.title = title;
        this.dueDate = dueDate;
        this.courseCode = courseCode;
        this.description = description;
    }

    // Getters and Setters
    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
        this.updatedAt = LocalDateTime.now();
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }
}