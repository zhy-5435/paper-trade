package com.stocksim.auth.service;

import com.stocksim.auth.dto.LoginRequest;
import com.stocksim.auth.dto.LoginResponse;
import com.stocksim.auth.dto.RegisterRequest;
import com.stocksim.auth.dto.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}