package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.BrandPriceBox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BrandPriceBoxMapper extends BaseMapper<BrandPriceBox> {
    @Select("SELECT * FROM ads_ext_brand_price_box ORDER BY q_median DESC")
    List<BrandPriceBox> listAll();
}
