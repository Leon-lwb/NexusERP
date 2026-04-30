package org.dromara.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.sch.domain.SchPlanTeam;

import java.io.Serial;
import java.io.Serializable;

/**
 * 计划班组关联视图对象 sch_plan_team
 *
 * @author nexus
 * @date 2025-12-26
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchPlanTeam.class)
public class SchPlanTeamVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @ExcelProperty(value = "记录ID")
    private Long recordId;

    /**
     * 排班计划ID
     */
    @ExcelProperty(value = "排班计划ID")
    private Long planId;

    /**
     * 班组ID
     */
    @ExcelProperty(value = "班组ID")
    private Long teamId;

    /**
     * 班组名称
     */
    @ExcelProperty(value = "班组名称")
    private String teamName;
}
