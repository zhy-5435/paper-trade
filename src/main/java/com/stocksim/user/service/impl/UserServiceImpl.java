package com.stocksim.user.service.impl;

import com.stocksim.auth.entity.SysUser;
import com.stocksim.auth.mapper.SysUserMapper;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.user.dto.ProfileResponse;
import com.stocksim.user.dto.UpdateProfileRequest;
import com.stocksim.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;

    public UserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public ProfileResponse getProfile(Long userId) {
        return toProfile(requireUser(userId));
    }

    @Override
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        requireUser(userId);
        if (request.nickname() != null || request.phone() != null || request.email() != null) {
            SysUser update = new SysUser();
            update.setId(userId);
            update.setNickname(request.nickname());
            update.setPhone(request.phone());
            update.setEmail(request.email());
            sysUserMapper.updateById(update);
        }
        return toProfile(requireUser(userId));
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private ProfileResponse toProfile(SysUser user) {
        return new ProfileResponse(user.getId(), user.getUsername(), user.getNickname(),
                maskPhone(user.getPhone()), user.getEmail(), user.getRole(), user.getCreateTime());
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}