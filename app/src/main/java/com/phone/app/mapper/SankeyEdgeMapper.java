package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.SankeyEdge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SankeyEdgeMapper extends BaseMapper<SankeyEdge> {
    @Select("SELECT * FROM ads_ext_sales_sankey ORDER BY layer_idx ASC, value DESC")
    List<SankeyEdge> listAll();
}
