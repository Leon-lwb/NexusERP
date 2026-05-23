package org.dromara.erp.wm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 条码配置业务对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WmBarcodeConfigBo extends BaseEntity {

    private Long configId;

    @NotBlank(message = "规则编码不能为空")
    private String configCode;

    @NotBlank(message = "规则名称不能为空")
    private String configName;

    private String prefix;

    private String datePattern;

    @NotNull(message = "流水号长度不能为空")
    private Integer sequenceLength;

    private Long withCheckDigit;

    private String bizType;

    private Long categoryId;

    private Long enableFlag;

    private String remark;
}
