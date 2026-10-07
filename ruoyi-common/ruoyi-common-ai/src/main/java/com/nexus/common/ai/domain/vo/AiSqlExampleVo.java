package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiSqlExample;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI SQL Few-Shot 示例视图对象
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiSqlExample.class)
public class AiSqlExampleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "示例ID")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 用户问题 */
    @ExcelProperty(value = "用户问题")
    private String question;

    /** SQL 模板 */
    @ExcelProperty(value = "SQL模板")
    private String sqlTemplate;

    /** 业务分类 */
    @ExcelProperty(value = "业务分类")
    private String category;

    /** 描述 */
    @ExcelProperty(value = "描述")
    private String description;

    /** 排序号 */
    @ExcelProperty(value = "排序号")
    private Integer sortOrder;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;

    /** 创建时间 */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;
}
