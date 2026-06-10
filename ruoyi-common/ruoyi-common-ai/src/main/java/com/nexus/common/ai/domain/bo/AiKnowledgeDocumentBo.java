package com.nexus.common.ai.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;

/**
 * AI知识库文档业务对象
 * <p>用于文档的新增/修改操作参数接收</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiKnowledgeDocument.class, reverseConvertGenerate = false)
public class AiKnowledgeDocumentBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 知识库ID */
    @NotNull(message = "知识库ID不能为空")
    private Long knowledgeId;

    /** 文件名 */
    private String fileName;

    /** OSS 文件路径 */
    private String filePath;

    /** 文件大小 */
    private Long fileSize;

    /** 分段数量 */
    private Integer chunkCount;

    /** 状态 */
    private String status;

    /** 批量删除ID集合 */
    private Long[] ids;
}
