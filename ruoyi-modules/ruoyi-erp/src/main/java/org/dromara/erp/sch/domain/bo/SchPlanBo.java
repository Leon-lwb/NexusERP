package org.dromara.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.sch.domain.SchPlan;

import java.util.Date;

/**
 * 排班计划业务对象 sch_plan
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchPlan.class, reverseConvertGenerate = false)
public class SchPlanBo extends BaseEntity {

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 计划编码
     */
    @NotBlank(message = "计划编码不能为空")
    private String planCode;

    /**
     * 计划名称
     */
    @NotBlank(message = "计划名称不能为空")
    private String planName;

    /**
     * 开始日期
     */
    private Date startDate;

    /**
     * 结束日期
     */
    private Date endDate;

    /**
     * 周期类型
     */
    private String periodType;

    /**
     * 轮班方式
     */
    private Integer rotationType;

    /**
     * 状态
     */
    private String status;

    /**
     * 发布人
     */
    private String publishedBy;

    /**
     * 发布时间
     */
    private Date publishedTime;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
