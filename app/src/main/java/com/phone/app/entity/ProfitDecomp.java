package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ads_profit_decomp")
public class ProfitDecomp {
    private String saleYm;
    private String compareType;      // mom / yoy
    private String baseYm;
    private Double profitCurr;
    private Double profitBase;
    private Double profitDelta;
    private String factor;           // unit_price/sales_qty/cost/marketing/other
    private String factorNameCn;     // 客单价/销量/成本/营销费用/其他
    private Double contribution;
    private Double contributionPct;
}
