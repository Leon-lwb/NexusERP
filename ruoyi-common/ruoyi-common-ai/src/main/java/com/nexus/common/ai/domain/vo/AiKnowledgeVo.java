package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI 知识库视图对象 ai_knowledge
 * <p>包含知识库完整信息，用于查询结果返回和 Excel 导出</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiKnowledge.class)
public class AiKnowledgeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 知识库名称 */
    @ExcelProperty(value = "知识库名称")
    private String knowledgeName;

    /** 描述 */
    @ExcelProperty(value = "描述")
    private String description;

    /** Embedding模型 */
    @ExcelProperty(value = "Embedding模型")
    private String embeddingModel;

    /** 向量存储类型 */
    @ExcelProperty(value = "向量存储类型")
    private String vectorStore;

    /** 向量索引状态（READY 就绪 / BUILDING 构建中 / FAILED 失败） */
    @ExcelProperty(value = "向量索引状态")
    private String vectorIndexStatus;

    /** 向量索引构建进度（0-100） */
    @ExcelProperty(value = "向量索引进度")
    private Integer vectorIndexProgress;

    /** 向量索引构建错误信息 */
    private String vectorIndexError;

    /** 向量索引更新时间 */
    private Date vectorIndexUpdatedTime;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "知识分隔符")
    private String separator;

    @ExcelProperty(value = "重叠字符数")
    private Integer overlapChar;

    @ExcelProperty(value = "文本块大小")
    private Integer textBlockSize;

    @ExcelProperty(value = "检索条数")
    private Integer retrieveLimit;

    @ExcelProperty(value = "相似度阈值")
    private Double similarityThreshold;

    @ExcelProperty(value = "启用重排序")
    private Integer enableRerank;

    @ExcelProperty(value = "重排序模型")
    private String rerankModel;

    @ExcelProperty(value = "启用混合检索")
    private Integer enableHybrid;

    @ExcelProperty(value = "混合检索权重")
    private Double hybridAlpha;

    @ExcelProperty(value = "系统提示词")
    private String systemPrompt;

    /** 文档数量（非数据库字段，用于展示） */
    @ExcelProperty(value = "文档数量")
    private Integer documentCount;

}
