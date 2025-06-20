package com.academicplanner.assignmentservice.exception;

public class CourseValidationException extends RuntimeException {
    public CourseValidationException(String message) {
        super(message);
    }

    public CourseValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}