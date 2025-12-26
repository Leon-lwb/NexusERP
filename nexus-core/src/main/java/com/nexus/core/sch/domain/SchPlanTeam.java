package com.nexus.core.sch.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 计划班组关联对象 sch_plan_team
 *
 * @author nexus
 * @date 2025-12-26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchPlanTeamMapper.SchPlanTeamResult")
public class SchPlanTeam extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(value = "record_id",type = IdType.AUTO)
    private Long recordId;
    /** 排班计划ID */
    @Excel(name = "排班计划ID")
    private Long planId;
    /** 班组ID */
    @Excel(name = "班组ID")
    private Long teamId;
    /** 班组名称 */
    @Excel(name = "班组名称")
    private String teamName;
}