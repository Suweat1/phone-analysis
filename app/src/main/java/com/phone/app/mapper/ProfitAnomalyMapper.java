package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.ProfitAnomaly;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ProfitAnomalyMapper extends BaseMapper<ProfitAnomaly> {

    @Select("SELECT * FROM ads_profit_anomaly " +
            "WHERE (#{from} IS NULL OR sale_date >= #{from}) " +
            "  AND (#{to}   IS NULL OR sale_date <= #{to}) " +
            "ORDER BY sale_date ASC")
    List<ProfitAnomaly> findInRange(@Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    @Select("SELECT * FROM ads_profit_anomaly WHERE is_anomaly = 1 " +
            "ORDER BY sale_date DESC LIMIT #{limit}")
    List<ProfitAnomaly> findRecentAnomalies(@Param("limit") int limit);
}
