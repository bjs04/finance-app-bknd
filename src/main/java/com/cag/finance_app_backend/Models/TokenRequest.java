package com.cag.finance_app_backend.Models;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank(message = "Token is required") String token) {
    
}
