package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 知识图谱关系业务对象
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiKgRelation.class, reverseConvertGenerate = false)
public class AiKgRelationBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 图谱ID */
    private Long graphId;

    /** 关系类型 */
    private String relationType;

    /** 头实体ID */
    private Long sourceEntityId;

    /** 尾实体ID */
    private Long targetEntityId;
}
