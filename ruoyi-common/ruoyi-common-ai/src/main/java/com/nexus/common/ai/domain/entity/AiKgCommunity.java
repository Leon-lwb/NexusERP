package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识图谱社区
 * <p>对应数据库表 ai_kg_community，存储社区检测结果及 LLM 生成的社区摘要（GraphRAG）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_kg_community")
public class AiKgCommunity extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 图谱ID */
    private Long graphId;

    /** 社区层级（v1 单层=0） */
    private Integer communityLevel;

    /** 父社区ID（v1 为空） */
    private Long parentCommunityId;

    /** 社区序号 */
    private Integer communityIndex;

    /** 社区摘要（LLM 生成） */
    private String summary;

    /** 摘要状态（0待生成 1生成中 2已完成 3失败） */
    private String summaryStatus;

    /** 社区内实体数 */
    private Integer entityCount;

    /** 社区内关系数 */
    private Integer relationCount;

    /** 摘要消耗token */
    private Long tokenUsed;

    /** 错误信息 */
    private String errorMessage;
}
