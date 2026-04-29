package org.dromara.generator.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 代码生成表设计器-设计保存表
 * 从ruoyi-online模块迁移过来
 *
 * @author nbacheng
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table_design")
public class GenTableDesign extends BaseEntity {

    @TableId("design_id")
    private Long designId;

    /** 设计名称 */
    @NotBlank(message = "设计名称不能为空")
    private String designName;

    /** 主表名（便于检索） */
    private String tableName;

    /** 主表备注（便于检索） */
    private String tableComment;

    /** 设计JSON内容，包含tables/relations等 */
    private String content;

    /** 关联的代码生成表ID */
    private Long genTableId;

    /** 数据源名称 */
    private String dataName;
}
