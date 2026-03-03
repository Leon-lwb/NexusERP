package com.nexus.online.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table_design")
public class GenTableDesign extends BaseEntity {

    @TableId("design_id")
    private Long designId;

    @TableField("design_name")
    private String designName;

    @TableField("table_name")
    private String tableName;

    @TableField("table_comment")
    private String tableComment;

    @TableField("content")
    private String content;
}
