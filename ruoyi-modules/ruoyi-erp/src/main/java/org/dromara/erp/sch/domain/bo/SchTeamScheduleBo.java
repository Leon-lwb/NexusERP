package org.dromara.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.sch.domain.SchTeamSchedule;

import java.util.Date;

/**
 * 班组排班（模板）业务对象 sch_team_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchTeamSchedule.class, reverseConvertGenerate = false)
public class SchTeamScheduleBo extends BaseEntity {

    /**
     * 班组排班ID
     */
    private Long teamScheduleId;

    /**
     * 排班计划ID
     */
    private Long planId;

    /**
     * 班组ID
     */
    private Long teamId;

    /**
     * 班次ID
     */
    private Long shiftId;

    /**
     * 排班日期
     */
    private Date scheduledDate;

    /**
     * 星期几(1-7)
     */
    private Long weekDay;

    /**
     * 是否节假日
     */
    private Integer isHoliday;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
