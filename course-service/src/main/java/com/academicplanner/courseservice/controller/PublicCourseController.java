package com.academicplanner.courseservice.controller;

import com.academicplanner.courseservice.entity.Course;
import com.academicplanner.courseservice.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/public")
@Tag(name = "Public Course Access", description = "Public endpoints for browsing courses without authentication")
@RequiredArgsConstructor
public class PublicCourseController {
    private final CourseService courseService;

    @Operation(
            summary = "Get all courses (public)",
            description = "Retrieve a list of all available courses. No authentication required."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved courses",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Course.class)
                    )
            )
    })
    @GetMapping
    public ResponseEntity<List<Course>> getAllCoursesPublic() {
        List<Course> courses = courseService.getAllCourses();
        return ResponseEntity.ok(courses);
    }

    @Operation(
            summary = "Get courses by department (public)",
            description = "Retrieve all courses belonging to a specific department. No authentication required."
    )
    @GetMapping("/department/{department}")
    public ResponseEntity<List<Course>> getCoursesByDepartmentPublic(
            @Parameter(description = "Department name", required = true, example = "Computer Science")
            @PathVariable String department) {
        List<Course> courses = courseService.getCoursesByDepartment(department);
        return ResponseEntity.ok(courses);
    }

    @Operation(
            summary = "Search courses by keyword (public)",
            description = "Search for courses using keywords. No authentication required."
    )
    @GetMapping("/search")
    public ResponseEntity<List<Course>> searchCoursesPublic(
            @Parameter(description = "Search keyword", required = true, example = "programming")
            @RequestParam String keyword) {
        List<Course> courses = courseService.searchCoursesByKeyword(keyword);
        return ResponseEntity.ok(courses);
    }

    @Operation(
            summary = "Get all departments (public)",
            description = "Retrieve a list of all available departments. No authentication required."
    )
    @GetMapping("/departments")
    public ResponseEntity<List<String>> getAllDepartmentsPublic() {
        List<String> departments = courseService.getAllDepartments();
        return ResponseEntity.ok(departments);
    }
}