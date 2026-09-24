package com.nexus.erp.prd.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * APS排程方案业务对象
 * <p>
 * 兼作列表查询条件与排程运行入参：执行排产时需填 startDate/horizonDays/strategy。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PrdSchedulePlanBo extends BaseEntity {

    /**
     * 排程方案ID
     */
    private Long planId;

    /**
     * 方案编号
     */
    private String planNo;

    /**
     * 方案名称
     */
    private String planName;

    /**
     * 车间ID(空=全部车间)
     */
    private Long workshopId;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 排产起始日(执行排产必填)
     */
    @NotNull(message = "排产起始日不能为空", groups = {Run.class})
    private Date startDate;

    /**
     * 排产跨度(天)
     */
    @NotNull(message = "排产跨度不能为空", groups = {Run.class})
    @Min(value = 1, message = "排产跨度至少1天", groups = {Run.class})
    @Max(value = 90, message = "排产跨度最多90天", groups = {Run.class})
    private Integer horizonDays;

    /**
     * 排序策略: EDD-最早交期, CR-临界比, PRIORITY-优先级
     */
    private String strategy;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态: COMPLETED-已生成, PUBLISHED-已发布
     */
    private String status;

    /**
     * 排程运行校验分组
     */
    public interface Run {
    }
}
