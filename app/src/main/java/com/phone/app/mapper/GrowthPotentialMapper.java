package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.GrowthPotential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GrowthPotentialMapper extends BaseMapper<GrowthPotential> {

    @Select("SELECT * FROM ads_growth_potential ORDER BY rank_no ASC LIMIT #{limit}")
    List<GrowthPotential> topN(@Param("limit") int limit);
}
