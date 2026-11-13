package com.stocksim.user.dto;

import java.time.LocalDateTime;

public record ProfileResponse(Long userId, String username, String nickname, String phone,
                              String email, Integer role, LocalDateTime createTime) {
}