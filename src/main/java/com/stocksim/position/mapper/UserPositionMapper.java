package com.stocksim.position.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stocksim.position.entity.UserPosition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPositionMapper extends BaseMapper<UserPosition> {
}