package com.stocksim.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stocksim.stock.entity.StockQuote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockQuoteMapper extends BaseMapper<StockQuote> {
}