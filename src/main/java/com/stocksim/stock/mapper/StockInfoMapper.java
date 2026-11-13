package com.stocksim.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stocksim.stock.entity.StockInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfo> {
}