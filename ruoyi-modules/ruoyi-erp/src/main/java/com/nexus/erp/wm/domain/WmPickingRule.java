package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * 拣货规则对象 wm_picking_rule
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_picking_rule")
public class WmPickingRule extends TenantEntity {

    /**
     * 规则ID
     */
    @TableId(value = "rule_id")
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
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
