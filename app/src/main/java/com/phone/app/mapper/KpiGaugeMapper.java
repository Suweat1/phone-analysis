package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.KpiGauge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KpiGaugeMapper extends BaseMapper<KpiGauge> {
    @Select("SELECT * FROM ads_ext_kpi_gauge ORDER BY kpi_code ASC")
    List<KpiGauge> listAll();
}
