package com.academicplanner.resourceservice.dto;

import com.academicplanner.resourceservice.entity.Resource;
import com.academicplanner.resourceservice.dto.shared.CourseDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class DepartmentResourcesDto {
    private String department;
    private List<CourseDto> courses;
    private List<Resource> resources;
    private int totalCourses;
    private int totalResources;

    public DepartmentResourcesDto(String department, List<CourseDto> courses, List<Resource> resources) {
        this.department = department;
        this.courses = courses;
        this.resources = resources;
        this.totalCourses = courses.size();
        this.totalResources = resources.size();
    }

    public void setCourses(List<CourseDto> courses) { 
        this.courses = courses;
        this.totalCourses = courses != null ? courses.size() : 0;
    }
    public void setResources(List<Resource> resources) { 
        this.resources = resources;
        this.totalResources = resources != null ? resources.size() : 0;
    }
}