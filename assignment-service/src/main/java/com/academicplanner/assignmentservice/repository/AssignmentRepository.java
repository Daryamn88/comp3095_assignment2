package com.academicplanner.assignmentservice.repository;

import com.academicplanner.assignmentservice.entity.Assignment;
import com.academicplanner.assignmentservice.entity.AssignmentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AssignmentRepository extends MongoRepository<Assignment, String> {
    
    List<Assignment> findByCourseCodeIgnoreCase(String courseCode);
    
    List<Assignment> findByStatus(AssignmentStatus status);
    
    @Query("{ 'dueDate' : { $gte: ?0, $lte: ?1 } }")
    List<Assignment> findByDueDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("{ 'courseCode' : ?0, 'dueDate' : { $gte: ?1, $lte: ?2 } }")
    List<Assignment> findByCourseCodeAndDueDateBetween(String courseCode, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("{ 'dueDate' : { $lt: ?0 }, 'status' : { $ne: 'COMPLETED' } }")
    List<Assignment> findOverdueAssignments(LocalDateTime currentDate);
}