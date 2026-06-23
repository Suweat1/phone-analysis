package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 桑基图边（layerIdx=0: 渠道→品牌, layerIdx=1: 品牌→年龄段）。 */
@Data
@TableName("ads_ext_sales_sankey")
public class SankeyEdge {
    private Integer layerIdx;
    private String  source;
    private String  target;
    private Double  value;
}
