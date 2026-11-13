package com.stocksim.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        @Size(max = 50) String nickname,
        @Pattern(regexp = "^1\\d{10}$") String phone,
        @Email @Size(max = 100) String email) {
}