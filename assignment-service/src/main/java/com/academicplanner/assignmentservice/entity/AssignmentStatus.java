package com.academicplanner.assignmentservice.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Assignment completion status")
public enum AssignmentStatus {
    @Schema(description = "Assignment has been created but not started")
    PENDING,

    @Schema(description = "Assignment is currently being worked on")
    IN_PROGRESS,

    @Schema(description = "Assignment has been completed")
    COMPLETED,

    @Schema(description = "Assignment is past due date and not completed")
    OVERDUE
}