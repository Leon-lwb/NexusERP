package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 知识库文档片段视图对象 ai_knowledge_chunk
 * <p>包含片段完整信息，用于查询结果返回和 Excel 导出</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiKnowledgeChunk.class)
public class AiKnowledgeChunkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 文档ID */
    @ExcelProperty(value = "文档ID")
    private Long documentId;

    /** 知识库ID */
    @ExcelProperty(value = "知识库ID")
    private Long knowledgeId;

    /** 片段内容 */
    @ExcelProperty(value = "片段内容")
    private String content;

    /** 片段序号 */
    @ExcelProperty(value = "片段序号")
    private Integer chunkIndex;

    /** 向量ID */
    @ExcelProperty(value = "向量ID")
    private String vectorId;

    /** 图谱抽取状态（0待处理 1处理中 2已完成 3失败） */
    @ExcelProperty(value = "抽取状态")
    private String extractionStatus;

    /** 抽取实体数 */
    @ExcelProperty(value = "抽取实体数")
    private Integer entityCount;

    /** 抽取关系数 */
    @ExcelProperty(value = "抽取关系数")
    private Integer relationCount;

    /** 抽取消耗token */
    @ExcelProperty(value = "抽取消耗token")
    private Long tokenUsed;

    /** 抽取错误信息 */
    private String kgErrorMessage;

}
