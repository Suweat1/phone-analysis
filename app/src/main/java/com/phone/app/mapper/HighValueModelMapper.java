package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.HighValueModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HighValueModelMapper extends BaseMapper<HighValueModel> {

    @Select("SELECT * FROM ads_high_value_model ORDER BY rank_no ASC LIMIT #{limit}")
    List<HighValueModel> topN(@Param("limit") int limit);
}
