package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;
import com.nexus.erp.wm.domain.vo.WmBarcodeConfigVo;

/**
 * 条码规则配置对象 wm_barcode_config
 *
 * 说明：
 * - 用于描述条码如何生成
 * - 后续可扩展为按业务类型、单据类型、物料类别分别配置
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_barcode_config")
@AutoMapper(target = WmBarcodeConfigVo.class)
public class WmBarcodeConfig extends TenantEntity {

    /** 条码配置ID */
    @TableId(value = "config_id")
    private Long configId;

    /** 规则编码 */
    private String configCode;

    /** 规则名称 */
    private String configName;

    /** 条码前缀 */
    private String prefix;

    /** 日期格式，例如 yyyyMMdd */
    private String datePattern;

    /** 流水号长度 */
    private Integer sequenceLength;

    /** 是否包含校验位 */
    private Long withCheckDigit;

    /** 适用业务类型 */
    private String bizType;

    /** 适用物料类别 */
    private Long categoryId;

    /** 是否启用 */
    private Long enableFlag;

    /** 备注 */
    private String remark;
}
