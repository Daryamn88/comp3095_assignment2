package com.academicplanner.assignmentservice.controller;

import com.academicplanner.assignmentservice.entity.Assignment;
import com.academicplanner.assignmentservice.entity.AssignmentStatus;
import com.academicplanner.assignmentservice.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/assignments")
@Tag(name = "Assignment Management", description = "Operations for managing student assignments")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @Operation(
            summary = "Get all assignments",
            description = "Retrieve a list of all assignments. Access level depends on user role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved assignments",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Assignment.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Insufficient permissions",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<List<Assignment>> getAllAssignments() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Students can only see their own assignments (in a real app, you'd filter by user)
        // For this demo, we'll show all assignments to all authenticated users
        List<Assignment> assignments = assignmentService.getAllAssignments();
        return ResponseEntity.ok(assignments);
    }

    @Operation(
            summary = "Get assignment by ID",
            description = "Retrieve a specific assignment by its unique identifier"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Assignment found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Assignment.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Assignment not found",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<Assignment> getAssignmentById(
            @Parameter(description = "Assignment ID", required = true, example = "507f1f77bcf86cd799439011")
            @PathVariable String id) {
        Optional<Assignment> assignment = assignmentService.getAssignmentById(id);
        return assignment.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get assignments by course",
            description = "Retrieve all assignments for a specific course"
    )
    @GetMapping("/course/{courseCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<List<Assignment>> getAssignmentsByCourse(
            @Parameter(description = "Course code", required = true, example = "CS101")
            @PathVariable String courseCode) {
        List<Assignment> assignments = assignmentService.getAssignmentsByCourseCode(courseCode);
        return ResponseEntity.ok(assignments);
    }

    @Operation(
            summary = "Get assignments by status",
            description = "Retrieve all assignments with a specific completion status"
    )
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<List<Assignment>> getAssignmentsByStatus(
            @Parameter(description = "Assignment status", required = true)
            @PathVariable AssignmentStatus status) {
        List<Assignment> assignments = assignmentService.getAssignmentsByStatus(status);
        return ResponseEntity.ok(assignments);
    }

    @Operation(
            summary = "Get assignments by due date range",
            description = "Retrieve assignments due within a specific date range"
    )
    @GetMapping("/due-date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<List<Assignment>> getAssignmentsByDueDateRange(
            @Parameter(description = "Start date (ISO format)", required = true, example = "2024-01-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)", required = true, example = "2024-12-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<Assignment> assignments = assignmentService.getAssignmentsByDueDateRange(startDate, endDate);
        return ResponseEntity.ok(assignments);
    }

    @Operation(
            summary = "Get overdue assignments",
            description = "Retrieve all assignments that are past due and not completed. Restricted to ADMIN and INSTRUCTOR roles."
    )
    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<Assignment>> getOverdueAssignments() {
        List<Assignment> assignments = assignmentService.getOverdueAssignments();
        return ResponseEntity.ok(assignments);
    }

    @Operation(
            summary = "Create a new assignment",
            description = "Create a new assignment. Requires ADMIN or INSTRUCTOR role. Validates course code against course service."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Assignment created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Assignment.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data or course code validation failed",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - ADMIN or INSTRUCTOR role required",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Assignment> createAssignment(
            @Parameter(description = "Assignment data to create", required = true)
            @Valid @RequestBody Assignment assignment) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Creating assignment - User: " + auth.getName() + ", Roles: " + auth.getAuthorities());

        Assignment createdAssignment = assignmentService.createAssignment(assignment);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAssignment);
    }

    @Operation(
            summary = "Update an existing assignment",
            description = "Update assignment information. Requires ADMIN or INSTRUCTOR role."
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Assignment> updateAssignment(
            @Parameter(description = "Assignment ID", required = true, example = "507f1f77bcf86cd799439011")
            @PathVariable String id,
            @Parameter(description = "Updated assignment data", required = true)
            @Valid @RequestBody Assignment assignment) {
        Optional<Assignment> existingAssignment = assignmentService.getAssignmentById(id);
        if (existingAssignment.isPresent()) {
            assignment.setAssignmentId(id);
            Assignment updatedAssignment = assignmentService.updateAssignment(assignment);
            return ResponseEntity.ok(updatedAssignment);
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(
            summary = "Mark assignment as completed",
            description = "Mark an assignment as completed. Students can mark their own assignments, instructors and admins can mark any assignment."
    )
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<Assignment> markAssignmentAsCompleted(
            @Parameter(description = "Assignment ID", required = true, example = "507f1f77bcf86cd799439011")
            @PathVariable String id) {
        Assignment assignment = assignmentService.markAsCompleted(id);
        if (assignment != null) {
            return ResponseEntity.ok(assignment);
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(
            summary = "Delete an assignment",
            description = "Remove an assignment from the system. Requires ADMIN or INSTRUCTOR role."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Void> deleteAssignment(
            @Parameter(description = "Assignment ID", required = true, example = "507f1f77bcf86cd799439011")
            @PathVariable String id) {
        Optional<Assignment> assignment = assignmentService.getAssignmentById(id);
        if (assignment.isPresent()) {
            assignmentService.deleteAssignment(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(
            summary = "Update overdue assignments",
            description = "Batch update assignments that are past due to OVERDUE status. Requires ADMIN role."
    )
    @PostMapping("/update-overdue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateOverdueAssignments() {
        assignmentService.updateOverdueAssignments();
        return ResponseEntity.ok().build();
    }
}