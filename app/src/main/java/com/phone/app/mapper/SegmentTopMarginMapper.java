package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.SegmentTopMargin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SegmentTopMarginMapper extends BaseMapper<SegmentTopMargin> {

    @Select("SELECT * FROM ads_segment_top_margin ORDER BY rank_no ASC LIMIT #{limit}")
    List<SegmentTopMargin> topN(@Param("limit") int limit);
}
