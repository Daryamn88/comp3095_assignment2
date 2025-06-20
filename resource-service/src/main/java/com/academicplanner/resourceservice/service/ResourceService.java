package com.academicplanner.resourceservice.service;

import com.academicplanner.resourceservice.client.CourseServiceClient;
import com.academicplanner.resourceservice.dto.DepartmentResourcesDto;
import com.academicplanner.resourceservice.entity.Resource;
import com.academicplanner.resourceservice.entity.ResourceCategory;
import com.academicplanner.resourceservice.repository.ResourceRepository;
import com.academicplanner.resourceservice.dto.shared.CourseDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private static final Logger logger = LoggerFactory.getLogger(ResourceService.class);
    private final ResourceRepository resourceRepository;
    private final CourseServiceClient courseServiceClient;
    private final CachedDataService cachedDataService;

    public List<Resource> getAllResources() {
        return resourceRepository.findAll();
    }

    public Optional<Resource> getResourceById(Long id) {
        return resourceRepository.findById(id);
    }

    public List<Resource> getResourcesByCategory(ResourceCategory category) {
        return resourceRepository.findByCategory(category);
    }

    public List<Resource> searchResourcesByKeyword(String keyword) {
        return resourceRepository.findByKeyword(keyword);
    }

    public List<Resource> searchResourcesByCategoryAndKeyword(ResourceCategory category, String keyword) {
        return resourceRepository.findByCategoryAndKeyword(category, keyword);
    }

    public DepartmentResourcesDto getResourcesForDepartment(String department) {
        logger.info("Getting resources for department: {}", department);

        // Try to get courses from course service with circuit breaker protection
        List<CourseDto> courses;

        // First try cache
        Optional<List<CourseDto>> cachedCourses = cachedDataService.getCachedCoursesByDepartment(department);
        if (cachedCourses.isPresent()) {
            logger.info("Using cached courses for department: {}", department);
            courses = cachedCourses.get();
        } else {
            // Fetch from service (protected by circuit breaker)
            courses = courseServiceClient.getCoursesByDepartment(department);

            // Cache the result if successful
            if (courses != null && !courses.isEmpty() &&
                    !courses.get(0).getTitle().contains("unavailable")) {
                cachedDataService.cacheCoursesByDepartment(department, courses);
            }
        }

        // Get relevant resources based on department
        List<Resource> academicResources = resourceRepository.findByCategory(ResourceCategory.ACADEMIC_SUPPORT);
        List<Resource> libraryResources = resourceRepository.findByCategory(ResourceCategory.LIBRARY);
        List<Resource> researchResources = resourceRepository.findByCategory(ResourceCategory.RESEARCH);

        // Combine relevant resources
        List<Resource> allRelevantResources = Stream.of(
                        academicResources, libraryResources, researchResources
                )
                .flatMap(List::stream)
                .toList();

        return new DepartmentResourcesDto(department, courses, allRelevantResources);
    }

    public List<String> getAvailableDepartments() {
        logger.info("Getting available departments");

        // Try cache first
        Optional<List<String>> cachedDepartments = cachedDataService.getCachedDepartments();
        if (cachedDepartments.isPresent()) {
            logger.info("Using cached departments");
            return cachedDepartments.get();
        }

        // Fetch from service (protected by circuit breaker)
        List<String> departments = courseServiceClient.getAllDepartments();

        // Cache the result if successful
        if (departments != null && !departments.isEmpty()) {
            cachedDataService.cacheDepartments(departments);
        }

        return departments;
    }

    public Resource createResource(Resource resource) {
        return resourceRepository.save(resource);
    }

    public Resource updateResource(Resource resource) {
        return resourceRepository.save(resource);
    }

    public void deleteResource(Long id) {
        resourceRepository.deleteById(id);
    }

    public void clearCache() {
        logger.info("Clearing resource service cache");
        cachedDataService.clearCache();
    }

    public Map<String, Object> getCacheStats() {
        return cachedDataService.getCacheStats();
    }
}