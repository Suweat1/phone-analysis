package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.BrandMonthHeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BrandMonthHeatMapper extends BaseMapper<BrandMonthHeat> {
    @Select("SELECT * FROM ads_ext_brand_month_heat ORDER BY brand ASC, sale_ym ASC")
    List<BrandMonthHeat> listAll();
}
