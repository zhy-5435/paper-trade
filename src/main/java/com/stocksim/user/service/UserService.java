package com.stocksim.user.service;

import com.stocksim.user.dto.ProfileResponse;
import com.stocksim.user.dto.UpdateProfileRequest;

public interface UserService {

    ProfileResponse getProfile(Long userId);

    ProfileResponse updateProfile(Long userId, UpdateProfileRequest request);
}