package com.phone.app.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ads_column_dict")
public class ColumnDict {
    @TableId
    private String columnEn;
    private String columnCn;
    private String layer;
    private String category;
}
