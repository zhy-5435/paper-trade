package com.stocksim.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stocksim.order.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {
}