package com.academicplanner.resourceservice.service;

import com.academicplanner.resourceservice.dto.shared.CourseDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CachedDataService {

    private final Map<String, List<String>> departmentCache = new ConcurrentHashMap<>();
    private final Map<String, List<CourseDto>> coursesByDepartmentCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> cacheTimestamps = new ConcurrentHashMap<>();

    private static final int CACHE_EXPIRY_MINUTES = 30;

    public void cacheDepartments(List<String> departments) {
        departmentCache.put("all", departments);
        cacheTimestamps.put("departments", LocalDateTime.now());
    }

    public Optional<List<String>> getCachedDepartments() {
        if (isCacheExpired("departments")) {
            return Optional.empty();
        }
        return Optional.ofNullable(departmentCache.get("all"));
    }

    public void cacheCoursesByDepartment(String department, List<CourseDto> courses) {
        coursesByDepartmentCache.put(department, courses);
        cacheTimestamps.put("courses_" + department, LocalDateTime.now());
    }

    public Optional<List<CourseDto>> getCachedCoursesByDepartment(String department) {
        if (isCacheExpired("courses_" + department)) {
            return Optional.empty();
        }
        return Optional.ofNullable(coursesByDepartmentCache.get(department));
    }

    public void clearCache() {
        departmentCache.clear();
        coursesByDepartmentCache.clear();
        cacheTimestamps.clear();
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("departmentCacheSize", departmentCache.size());
        stats.put("coursesCacheSize", coursesByDepartmentCache.size());
        stats.put("cacheTimestamps", cacheTimestamps);
        stats.put("cacheExpiryMinutes", CACHE_EXPIRY_MINUTES);
        return stats;
    }

    private boolean isCacheExpired(String key) {
        LocalDateTime timestamp = cacheTimestamps.get(key);
        if (timestamp == null) {
            return true;
        }
        return timestamp.isBefore(LocalDateTime.now().minusMinutes(CACHE_EXPIRY_MINUTES));
    }
}