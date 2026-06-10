package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识库文档实体
 * <p>对应数据库表 ai_knowledge_document，记录上传到知识库的文档信息及处理状态</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_document")
public class AiKnowledgeDocument extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 知识库ID */
    private Long knowledgeId;

    /** 文件名 */
    private String fileName;

    /** OSS 文件路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 分段数量 */
    private Integer chunkCount;

    /** 状态（0待解析 1解析中 2已完成 3失败） */
    private String status;

    /** 错误信息 */
    private String errorMessage;
}
