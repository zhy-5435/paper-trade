package com.stocksim.user.controller;

import com.stocksim.common.Result;
import com.stocksim.common.context.UserContext;
import com.stocksim.user.dto.ProfileResponse;
import com.stocksim.user.dto.UpdateProfileRequest;
import com.stocksim.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "个人信息")
    @GetMapping("/profile")
    public Result<ProfileResponse> profile() {
        return Result.ok(userService.getProfile(UserContext.userId()));
    }

    @Operation(summary = "修改个人信息")
    @PutMapping("/profile")
    public Result<ProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(UserContext.userId(), request));
    }
}