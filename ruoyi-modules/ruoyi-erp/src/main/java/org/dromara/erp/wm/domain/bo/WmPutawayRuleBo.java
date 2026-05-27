package org.dromara.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.erp.wm.domain.WmPutawayRule;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmPutawayRule.class, reverseConvertGenerate = false)
public class WmPutawayRuleBo extends WmPutawayRule {

    private Long[] ids;
}
