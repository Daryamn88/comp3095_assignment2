package com.academicplanner.resourceservice.dto.shared;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidationResponse {
    private boolean valid;
    private String message;
    private String entity;

    public static ValidationResponse valid(String entity) {
        return new ValidationResponse(true, "Valid", entity);
    }

    public static ValidationResponse invalid(String message, String entity) {
        return new ValidationResponse(false, message, entity);
    }
}