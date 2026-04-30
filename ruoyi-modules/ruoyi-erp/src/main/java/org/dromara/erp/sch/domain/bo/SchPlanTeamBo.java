package org.dromara.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.sch.domain.SchPlanTeam;

/**
 * 计划班组关联业务对象 sch_plan_team
 *
 * @author nexus
 * @date 2025-12-26
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchPlanTeam.class, reverseConvertGenerate = false)
public class SchPlanTeamBo extends BaseEntity {

    /**
     * 记录ID
     */
    private Long recordId;

    /**
     * 排班计划ID
     */
    private Long planId;

    /**
     * 班组ID
     */
    private Long teamId;

    /**
     * 班组名称
     */
    private String teamName;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
