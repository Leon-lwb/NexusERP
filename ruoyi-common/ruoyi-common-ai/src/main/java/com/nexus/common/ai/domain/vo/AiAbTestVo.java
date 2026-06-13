package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI A/B 测试 Vo
 *
 * @author NexusERP
 */
@Data
public class AiAbTestVo {

    /** 主键 */
    private Long id;

    /** 测试名称 */
    @ExcelProperty("测试名称")
    private String testName;

    /** 测试描述 */
    @ExcelProperty("描述")
    private String description;

    /** 模型A的ID */
    private Long modelAId;

    /** 模型A名称 */
    @ExcelProperty("模型A")
    private String modelAName;

    /** 模型B的ID */
    private Long modelBId;

    /** 模型B名称 */
    @ExcelProperty("模型B")
    private String modelBName;

    /** 测试问题列表（JSON数组） */
    private String testQuestions;

    /** 状态：PENDING/RUNNING/COMPLETED */
    @ExcelProperty("状态")
    private String status;

    /** 结果摘要（JSON） */
    private String resultSummary;

    /** 创建时间 */
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;
}
