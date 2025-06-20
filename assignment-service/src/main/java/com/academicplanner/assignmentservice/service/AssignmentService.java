package com.academicplanner.assignmentservice.service;

import com.academicplanner.assignmentservice.client.CourseServiceClient;
import com.academicplanner.assignmentservice.entity.Assignment;
import com.academicplanner.assignmentservice.entity.AssignmentStatus;
import com.academicplanner.assignmentservice.exception.CourseValidationException;
import com.academicplanner.assignmentservice.repository.AssignmentRepository;
import com.academicplanner.assignmentservice.dto.shared.ValidationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssignmentService {
    private final AssignmentRepository assignmentRepository;
    private final CourseServiceClient courseServiceClient;
    
    public List<Assignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }
    
    public Optional<Assignment> getAssignmentById(String id) {
        return assignmentRepository.findById(id);
    }
    
    public List<Assignment> getAssignmentsByCourseCode(String courseCode) {
        return assignmentRepository.findByCourseCodeIgnoreCase(courseCode);
    }
    
    public List<Assignment> getAssignmentsByStatus(AssignmentStatus status) {
        return assignmentRepository.findByStatus(status);
    }
    
    public List<Assignment> getAssignmentsByDueDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return assignmentRepository.findByDueDateBetween(startDate, endDate);
    }
    
    public List<Assignment> getAssignmentsByCourseAndDateRange(String courseCode, LocalDateTime startDate, LocalDateTime endDate) {
        return assignmentRepository.findByCourseCodeAndDueDateBetween(courseCode, startDate, endDate);
    }
    
    public List<Assignment> getOverdueAssignments() {
        return assignmentRepository.findOverdueAssignments(LocalDateTime.now());
    }
    
    public Assignment createAssignment(Assignment assignment) {
        // Validate course code before creating assignment
        ValidationResponse validation = courseServiceClient.validateCourseCode(assignment.getCourseCode());
        
        if (!validation.isValid()) {
            throw new CourseValidationException(
                "Cannot create assignment: " + validation.getMessage() + 
                " for course code: " + assignment.getCourseCode()
            );
        }
        
        return assignmentRepository.save(assignment);
    }
    
    public Assignment updateAssignment(Assignment assignment) {
        // Validate course code if it's being changed
        if (assignment.getAssignmentId() != null) {
            Optional<Assignment> existingAssignment = assignmentRepository.findById(assignment.getAssignmentId());
            if (existingAssignment.isPresent()) {
                String existingCourseCode = existingAssignment.get().getCourseCode();
                if (!existingCourseCode.equals(assignment.getCourseCode())) {
                    ValidationResponse validation = courseServiceClient.validateCourseCode(assignment.getCourseCode());
                    if (!validation.isValid()) {
                        throw new CourseValidationException(
                            "Cannot update assignment: " + validation.getMessage() + 
                            " for course code: " + assignment.getCourseCode()
                        );
                    }
                }
            }
        }
        
        assignment.setUpdatedAt(LocalDateTime.now());
        return assignmentRepository.save(assignment);
    }
    
    public Assignment markAsCompleted(String id) {
        Optional<Assignment> assignmentOpt = assignmentRepository.findById(id);
        if (assignmentOpt.isPresent()) {
            Assignment assignment = assignmentOpt.get();
            assignment.setStatus(AssignmentStatus.COMPLETED);
            return assignmentRepository.save(assignment);
        }
        return null;
    }
    
    public void deleteAssignment(String id) {
        assignmentRepository.deleteById(id);
    }
    
    public void updateOverdueAssignments() {
        List<Assignment> overdueAssignments = getOverdueAssignments();
        for (Assignment assignment : overdueAssignments) {
            assignment.setStatus(AssignmentStatus.OVERDUE);
            assignmentRepository.save(assignment);
        }
    }
}