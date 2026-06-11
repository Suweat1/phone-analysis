package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.MetricTrend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MetricTrendMapper extends BaseMapper<MetricTrend> {

    @Select("SELECT * FROM ads_metric_trend " +
            "WHERE metric_code = #{code} " +
            "  AND (#{from} IS NULL OR sale_date >= #{from}) " +
            "  AND (#{to}   IS NULL OR sale_date <= #{to}) " +
            "ORDER BY sale_date ASC")
    List<MetricTrend> findByCode(@Param("code") String code,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Select("SELECT DISTINCT metric_code, metric_name_cn FROM ads_metric_trend")
    List<MetricTrend> listMetrics();
}
