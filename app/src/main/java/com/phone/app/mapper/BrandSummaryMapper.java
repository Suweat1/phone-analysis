package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.BrandSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BrandSummaryMapper extends BaseMapper<BrandSummary> {
    @Select("SELECT * FROM ads_ext_brand_summary ORDER BY total_revenue DESC")
    List<BrandSummary> listAll();
}
