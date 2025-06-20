package com.academicplanner.resourceservice.repository;

import com.academicplanner.resourceservice.entity.Resource;
import com.academicplanner.resourceservice.entity.ResourceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {
    
    List<Resource> findByCategory(ResourceCategory category);
    
    @Query("SELECT r FROM Resource r WHERE " +
           "LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Resource> findByKeyword(@Param("keyword") String keyword);
    
    @Query("SELECT r FROM Resource r WHERE r.category = :category AND " +
           "(LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Resource> findByCategoryAndKeyword(@Param("category") ResourceCategory category, @Param("keyword") String keyword);
}