package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiKgCommunity;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 知识图谱社区业务对象
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiKgCommunity.class, reverseConvertGenerate = false)
public class AiKgCommunityBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 图谱ID */
    private Long graphId;

    /** 摘要状态（0待生成 1生成中 2已完成 3失败） */
    private String summaryStatus;
}
