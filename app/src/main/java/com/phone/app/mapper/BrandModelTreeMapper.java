package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.BrandModelNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BrandModelTreeMapper extends BaseMapper<BrandModelNode> {
    @Select("SELECT * FROM ads_ext_brand_model_tree ORDER BY brand ASC, total_revenue DESC")
    List<BrandModelNode> listAll();
}
