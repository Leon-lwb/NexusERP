package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiKgCommunity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 知识图谱社区视图对象 ai_kg_community
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiKgCommunity.class)
public class AiKgCommunityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** 租户编号 */
    private String tenantId;

    /** 图谱ID */
    private Long graphId;

    /** 社区层级（v1 单层=0） */
    private Integer communityLevel;

    /** 社区序号 */
    @ExcelProperty(value = "社区序号")
    private Integer communityIndex;

    /** 社区摘要（LLM 生成） */
    @ExcelProperty(value = "社区摘要")
    private String summary;

    /** 摘要状态（0待生成 1生成中 2已完成 3失败） */
    @ExcelProperty(value = "摘要状态")
    private String summaryStatus;

    /** 社区内实体数 */
    @ExcelProperty(value = "实体数")
    private Integer entityCount;

    /** 社区内关系数 */
    @ExcelProperty(value = "关系数")
    private Integer relationCount;

    /** 摘要消耗token */
    private Long tokenUsed;

    /** 错误信息 */
    private String errorMessage;
}
