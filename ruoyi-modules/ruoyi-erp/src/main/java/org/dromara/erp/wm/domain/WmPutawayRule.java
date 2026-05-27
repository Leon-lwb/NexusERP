package org.dromara.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
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
@TableName("wm_putaway_rule")
public class WmPutawayRule extends TenantEntity {

    @TableId(value = "rule_id")
    private Long ruleId;

    @ExcelProperty(value = "规则编码")
    private String ruleCode;

    @ExcelProperty(value = "规则名称")
    private String ruleName;

    private Long warehouseId;

    private Long materialCategoryId;

    @ExcelProperty(value = "策略类型")
    private String strategyType;

    @ExcelProperty(value = "优先级")
    private Integer priority;

    @ExcelProperty(value = "状态")
    private String status;

    private String remark;

    @TableLogic
    private String delFlag;
}
