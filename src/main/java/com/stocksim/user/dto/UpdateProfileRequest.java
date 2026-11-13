package com.stocksim.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50) String nickname,
        @Pattern(regexp = "^1\\d{10}$") String phone,
        @Email @Size(max = 100) String email) {
}