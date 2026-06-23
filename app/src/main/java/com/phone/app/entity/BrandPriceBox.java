package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 品牌客单价箱线（5 数概括 + 离群点）。 */
@Data
@TableName("ads_ext_brand_price_box")
public class BrandPriceBox {
    private String brand;
    private Double qMin;
    private Double q1;
    private Double qMedian;
    private Double q3;
    private Double qMax;
    private String outliers;       // 逗号分隔
}
