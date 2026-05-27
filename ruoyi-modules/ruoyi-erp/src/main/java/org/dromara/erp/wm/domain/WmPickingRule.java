package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_picking_rule")
public class WmPickingRule extends TenantEntity {

    @TableId(value = "rule_id")
    private Long ruleId;

    private String ruleCode;

    private String ruleName;

    private Long warehouseId;

    private Long materialCategoryId;

    private String strategyType;

    private Integer priority;

    private String status;

    @TableLogic
    private String delFlag;

    private String remark;
}
