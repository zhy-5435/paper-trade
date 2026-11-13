package com.stocksim.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stocksim.account.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}