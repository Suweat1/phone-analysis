package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ads_segment_top_margin")
public class SegmentTopMargin {
    private Integer rankNo;
    private String brand;
    private String userCity;
    private String ageGroup;
    private String userMemberLevel;
    private Double totalRevenue;
    private Double totalGrossProfit;
    private Double grossMargin;
    private Long orderCnt;
    private String segmentLabelCn;
}
