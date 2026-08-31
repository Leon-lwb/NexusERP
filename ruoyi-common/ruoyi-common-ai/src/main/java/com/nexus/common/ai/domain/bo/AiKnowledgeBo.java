package com.nexus.common.ai.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.common.ai.domain.entity.AiKnowledge;

import java.util.Date;

/**
 * AI知识库业务对象
 * <p>用于知识库的新增/修改操作参数接收</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiKnowledge.class, reverseConvertGenerate = false)
public class AiKnowledgeBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    private String knowledgeName;

    /** 描述 */
    private String description;

    /** 使用的 Embedding 模型 */
    private String embeddingModel;

    /** 向量存储类型 */
    private String vectorStore;

    /** 向量索引状态（READY 就绪 / REBUILDING 重建中 / FAILED 失败，字典 ai_knowledge_index_status；系统列，客户端写入会被剥离） */
    private String vectorIndexStatus;

    /** 向量索引构建进度（0-100） */
    private Integer vectorIndexProgress;

    /** 向量索引构建错误信息 */
    private String vectorIndexError;

    /** 向量索引更新时间 */
    private Date vectorIndexUpdatedTime;

    /** 是否重建向量索引（仅请求标记，不落库） */
    private Boolean rebuildIndex = false;

    /** 状态 */
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

    /** 混合检索权重 */
    private Double hybridAlpha;

    /** 系统提示词 */
    private String systemPrompt;

    /** 批量删除ID集合 */
    private Long[] ids;
}
