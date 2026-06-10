package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识库实体
 * <p>对应数据库表 ai_knowledge，包含知识库基本信息和 RAG 检索配置参数</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge")
public class AiKnowledge extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 知识库名称 */
    private String knowledgeName;

    /** 描述 */
    private String description;

    /** 使用的 Embedding 模型 */
    private String embeddingModel;

    /** 向量存储类型，默认 milvus */
    private String vectorStore;

    /** 状态（1正常 0停用） */
    private String status;

    /** 知识分隔符 */
    private String separator;

    /** 重叠字符数 */
    private Integer overlapChar;

    /** 文本块大小 */
    private Integer textBlockSize;

    /** 检索条数 */
    private Integer retrieveLimit;

    /** 相似度阈值 */
    private Double similarityThreshold;

    /** 是否启用重排序（0否 1是） */
    private Integer enableRerank;

    /** 重排序模型名称 */
    private String rerankModel;

    /** 是否启用混合检索（0否 1是） */
    private Integer enableHybrid;

    /** 混合检索权重（0=纯向量 1=纯关键词） */
    private Double hybridAlpha;

    /** 系统提示词 */
    private String systemPrompt;
}
