package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmPickingRule;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmPickingRule.class, reverseConvertGenerate = false)
public class WmPickingRuleBo extends BaseEntity {

    private Long ruleId;

    private String ruleCode;

    private String ruleName;

    private Long warehouseId;

    private Long materialCategoryId;

    private String strategyType;

    private Integer priority;

    private String status;

    private String remark;

    private Long[] ids;
}
