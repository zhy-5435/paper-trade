package com.stocksim.match.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stocksim.match.entity.DealRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DealRecordMapper extends BaseMapper<DealRecord> {

    @Select("SELECT MAX(deal_seq) FROM deal_record")
    Long selectMaxDealSeq();
}