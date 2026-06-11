package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.LowContribModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LowContribModelMapper extends BaseMapper<LowContribModel> {

    @Select("SELECT * FROM ads_low_contrib_model ORDER BY rank_no ASC LIMIT #{limit}")
    List<LowContribModel> topN(@Param("limit") int limit);
}
