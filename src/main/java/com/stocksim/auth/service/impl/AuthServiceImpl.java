package com.stocksim.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.account.service.AccountService;
import com.stocksim.auth.dto.LoginRequest;
import com.stocksim.auth.dto.LoginResponse;
import com.stocksim.auth.dto.RegisterRequest;
import com.stocksim.auth.dto.RegisterResponse;
import com.stocksim.auth.entity.SysUser;
import com.stocksim.auth.mapper.SysUserMapper;
import com.stocksim.auth.service.AuthService;
import com.stocksim.auth.service.JwtService;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.TradeCalendar;
import com.stocksim.common.audit.OperationAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final SysUserMapper sysUserMapper;
    private final AccountService accountService;
    private final JwtService jwtService;
    private final OperationAuditService operationAuditService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(SysUserMapper sysUserMapper, AccountService accountService, JwtService jwtService,
                           OperationAuditService operationAuditService) {
        this.sysUserMapper = sysUserMapper;
        this.accountService = accountService;
        this.jwtService = jwtService;
        this.operationAuditService = operationAuditService;
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username()));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setRole(1);
        user.setStatus(1);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        try {
            accountService.openAccount(user.getId());
        } catch (Exception e) {
            log.warn("注册后开户失败，将在首次登录/入金时补偿, userId={}", user.getId(), e);
        }
        return new RegisterResponse(user.getId(), user.getUsername());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, user.getStatus() == 0 ? "账号已被禁用" : "账号已注销");
        }
        if (user.getRole() == 1) {
            try {
                accountService.openAccount(user.getId());
            } catch (Exception e) {
                log.warn("登录补偿开户失败, userId={}", user.getId(), e);
            }
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now(TradeCalendar.ZONE));
        update.setVersion(user.getVersion());
        sysUserMapper.updateById(update);
        String token = jwtService.generate(user.getId(), user.getUsername(), user.getRole());
        operationAuditService.recordLogin(user.getId(), user.getUsername(), new Object[]{request});
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRole(), user.getNickname());
    }
}