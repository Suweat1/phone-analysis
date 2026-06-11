package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.LowContribChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LowContribChannelMapper extends BaseMapper<LowContribChannel> {

    @Select("SELECT * FROM ads_low_contrib_channel ORDER BY rank_no ASC LIMIT #{limit}")
    List<LowContribChannel> topN(@Param("limit") int limit);
}
