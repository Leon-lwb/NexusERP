package org.dromara.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 计划班组关联对象 sch_plan_team
 *
 * @author nexus
 * @date 2025-12-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_plan_team")
public class SchPlanTeam extends TenantEntity {

    /**
     * 记录ID
     */
    @TableId(value = "record_id")
    private Long recordId;
    /**
     * 排班计划ID
     */
    @ExcelProperty("排班计划ID")
    private Long planId;
    /**
     * 班组ID
     */
    @ExcelProperty("班组ID")
    private Long teamId;
    /**
     * 班组名称
     */
    @ExcelProperty("班组名称")
    private String teamName;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
