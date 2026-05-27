package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.erp.wm.domain.WmPutawayRule;

import java.io.Serial;
import java.io.Serializable;

@Data
@ExcelIgnoreUnannotated
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmPutawayRule.class)
public class WmPutawayRuleVo extends WmPutawayRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
