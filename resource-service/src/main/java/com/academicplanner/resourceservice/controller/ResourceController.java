package com.academicplanner.resourceservice.controller;

import com.academicplanner.resourceservice.dto.DepartmentResourcesDto;
import com.academicplanner.resourceservice.entity.Resource;
import com.academicplanner.resourceservice.entity.ResourceCategory;
import com.academicplanner.resourceservice.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {
    private final ResourceService resourceService;
    
    @GetMapping
    public ResponseEntity<List<Resource>> getAllResources() {
        List<Resource> resources = resourceService.getAllResources();
        return ResponseEntity.ok(resources);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Resource> getResourceById(@PathVariable Long id) {
        Optional<Resource> resource = resourceService.getResourceById(id);
        return resource.map(ResponseEntity::ok)
                      .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Resource>> getResourcesByCategory(@PathVariable ResourceCategory category) {
        List<Resource> resources = resourceService.getResourcesByCategory(category);
        return ResponseEntity.ok(resources);
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Resource>> searchResources(@RequestParam String keyword) {
        List<Resource> resources = resourceService.searchResourcesByKeyword(keyword);
        return ResponseEntity.ok(resources);
    }
    
    @GetMapping("/search/category/{category}")
    public ResponseEntity<List<Resource>> searchResourcesByCategory(
            @PathVariable ResourceCategory category,
            @RequestParam String keyword) {
        List<Resource> resources = resourceService.searchResourcesByCategoryAndKeyword(category, keyword);
        return ResponseEntity.ok(resources);
    }
    
    @PostMapping
    public ResponseEntity<Resource> createResource(@Valid @RequestBody Resource resource) {
        Resource createdResource = resourceService.createResource(resource);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdResource);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Resource> updateResource(@PathVariable Long id, @Valid @RequestBody Resource resource) {
        Optional<Resource> existingResource = resourceService.getResourceById(id);
        if (existingResource.isPresent()) {
            resource.setId(id);
            Resource updatedResource = resourceService.updateResource(resource);
            return ResponseEntity.ok(updatedResource);
        }
        return ResponseEntity.notFound().build();
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable Long id) {
        Optional<Resource> resource = resourceService.getResourceById(id);
        if (resource.isPresent()) {
            resourceService.deleteResource(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/departments")
    public ResponseEntity<List<String>> getAvailableDepartments() {
        List<String> departments = resourceService.getAvailableDepartments();
        return ResponseEntity.ok(departments);
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<DepartmentResourcesDto> getResourcesForDepartment(@PathVariable String department) {
        DepartmentResourcesDto departmentResources = resourceService.getResourcesForDepartment(department);
        return ResponseEntity.ok(departmentResources);
    }
}