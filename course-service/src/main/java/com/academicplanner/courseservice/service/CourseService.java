package com.academicplanner.courseservice.service;

import com.academicplanner.courseservice.entity.Course;
import com.academicplanner.courseservice.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    
    @Cacheable(value = "courses")
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }
    
    @Cacheable(value = "course", key = "#id")
    public Optional<Course> getCourseById(Long id) {
        return courseRepository.findById(id);
    }
    
    @Cacheable(value = "course", key = "#courseCode")
    public Optional<Course> getCourseByCourseCode(String courseCode) {
        return courseRepository.findByCourseCode(courseCode);
    }
    
    @Cacheable(value = "coursesByDepartment", key = "#department")
    public List<Course> getCoursesByDepartment(String department) {
        return courseRepository.findByDepartmentIgnoreCase(department);
    }
    
    @Cacheable(value = "coursesByKeyword", key = "#keyword")
    public List<Course> searchCoursesByKeyword(String keyword) {
        return courseRepository.findByKeyword(keyword);
    }
    
    @Cacheable(value = "departments")
    public List<String> getAllDepartments() {
        return courseRepository.findAllDepartments();
    }
    
    @CacheEvict(value = {"courses", "coursesByDepartment", "departments", "coursesByKeyword"}, allEntries = true)
    public Course createCourse(Course course) {
        return courseRepository.save(course);
    }
    
    @CachePut(value = "course", key = "#result.id")
    @CacheEvict(value = {"courses", "coursesByDepartment", "departments", "coursesByKeyword"}, allEntries = true)
    public Course updateCourse(Course course) {
        return courseRepository.save(course);
    }
    
    @CacheEvict(value = {"course", "courses", "coursesByDepartment", "departments", "coursesByKeyword"}, allEntries = true)
    public void deleteCourse(Long id) {
        courseRepository.deleteById(id);
    }
}