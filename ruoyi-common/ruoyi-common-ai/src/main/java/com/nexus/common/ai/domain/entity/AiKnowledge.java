package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

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

    /** 向量索引状态（READY 就绪 / REBUILDING 重建中 / FAILED 失败，字典 ai_knowledge_index_status） */
    private String vectorIndexStatus;

    /** 向量索引构建进度（0-100） */
    private Integer vectorIndexProgress;

    /** 向量索引构建错误信息 */
    private String vectorIndexError;

    /** 向量索引更新时间 */
    private Date vectorIndexUpdatedTime;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    private String status;

    /** 知识分隔符（MySQL 保留关键字，需反引号转义） */
    @TableField("`separator`")
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
