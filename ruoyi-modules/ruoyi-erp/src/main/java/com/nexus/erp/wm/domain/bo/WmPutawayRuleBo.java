package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.erp.wm.domain.WmPutawayRule;

/**
 * 上架规则业务对象 wm_putaway_rule
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmPutawayRule.class, reverseConvertGenerate = false)
public class WmPutawayRuleBo extends WmPutawayRule {

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}
