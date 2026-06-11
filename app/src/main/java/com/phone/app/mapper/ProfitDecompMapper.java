package com.phone.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phone.app.entity.ProfitDecomp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProfitDecompMapper extends BaseMapper<ProfitDecomp> {

    /**
     * @param compareType mom 或 yoy
     * @param ym          指定年月（如 2024-08）；为 null 时返回全部
     */
    @Select("SELECT * FROM ads_profit_decomp " +
            "WHERE compare_type = #{compareType} " +
            "  AND (#{ym} IS NULL OR sale_ym = #{ym}) " +
            "ORDER BY sale_ym ASC, factor ASC")
    List<ProfitDecomp> find(@Param("compareType") String compareType,
                            @Param("ym") String ym);

    @Select("SELECT DISTINCT sale_ym FROM ads_profit_decomp ORDER BY sale_ym DESC LIMIT 24")
    List<String> recentMonths();
}
