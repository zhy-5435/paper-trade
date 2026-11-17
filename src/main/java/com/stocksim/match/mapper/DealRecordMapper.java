package com.stocksim.match.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stocksim.match.entity.DealRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DealRecordMapper extends BaseMapper<DealRecord> {

    @Select("SELECT MAX(deal_seq) FROM deal_record")
    Long selectMaxDealSeq();

    @Select("SELECT COALESCE(SUM(deal_qty), 0) FROM deal_record "
            + "WHERE buyer_order_no = #{orderNo} OR seller_order_no = #{orderNo}")
    long sumDealQtyByOrderNo(@Param("orderNo") String orderNo);
}