package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmBarcodeConfig;

/**
 * 条码配置业务对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmBarcodeConfig.class)
public class WmBarcodeConfigBo extends BaseEntity {

    /**
     * 配置ID
     */
    private Long configId;

    /**
     * 规则编码
     */
    @NotBlank(message = "规则编码不能为空")
    private String configCode;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    private String configName;

    /**
     * 条码前缀
     */
    private String prefix;

    /**
     * 日期格式
     */
    private String datePattern;

    /**
     * 流水号长度
     */
    @NotNull(message = "流水号长度不能为空")
    private Integer sequenceLength;

    /**
     * 是否包含校验位
     */
    private Long withCheckDigit;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 物料类别ID
     */
    private Long categoryId;

    /**
     * 启用标志
     */
    private Long enableFlag;

    /**
     * 当前序列号
     */
    private Long currentSequence;

    /**
     * 备注
     */
    private String remark;
}
