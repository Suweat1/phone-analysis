package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 品牌→机型 层级节点（Treemap / Sunburst 用）。 */
@Data
@TableName("ads_ext_brand_model_tree")
public class BrandModelNode {
    private String brand;
    private String model;
    private Double totalRevenue;
    private Double totalGrossProfit;
    private Double grossMargin;
    private Long   totalQty;
}
