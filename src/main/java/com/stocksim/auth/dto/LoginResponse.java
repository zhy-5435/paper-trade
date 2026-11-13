package com.stocksim.auth.dto;

public record LoginResponse(String token, Long userId, String username, Integer role, String nickname) {
}