package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 知识库文档视图对象 ai_knowledge_document
 * <p>包含文档完整信息，用于查询结果返回和 Excel 导出</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiKnowledgeDocument.class)
public class AiKnowledgeDocumentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 知识库ID */
    @ExcelProperty(value = "知识库ID")
    private Long knowledgeId;

    /** 文件名 */
    @ExcelProperty(value = "文件名")
    private String fileName;

    /** 文件路径 */
    @ExcelProperty(value = "文件路径")
    private String filePath;

    /** 文件大小 */
    @ExcelProperty(value = "文件大小")
    private Long fileSize;

    /** 分段数量 */
    @ExcelProperty(value = "分段数量")
    private Integer chunkCount;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;

    /** 错误信息 */
    @ExcelProperty(value = "错误信息")
    private String errorMessage;

}
