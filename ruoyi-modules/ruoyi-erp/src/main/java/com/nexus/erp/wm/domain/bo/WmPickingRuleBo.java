package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmPickingRule;

/**
 * 拣货规则业务对象 wm_picking_rule
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmPickingRule.class, reverseConvertGenerate = false)
public class WmPickingRuleBo extends BaseEntity {

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则编码
     */
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 物料类别ID
     */
    private Long materialCategoryId;

    /**
     * 策略类型
     */
    private String strategyType;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}
