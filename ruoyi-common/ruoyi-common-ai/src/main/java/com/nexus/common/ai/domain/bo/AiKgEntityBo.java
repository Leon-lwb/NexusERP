package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 知识图谱实体业务对象
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiKgEntity.class, reverseConvertGenerate = false)
public class AiKgEntityBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 图谱ID */
    private Long graphId;

    /** 知识库ID（用于按知识库查询其图谱下的实体） */
    private Long knowledgeId;

    /** 实体名称 */
    private String entityName;

    /** 实体类型 */
    private String entityType;

    /** 所属社区ID */
    private Long communityId;
}
