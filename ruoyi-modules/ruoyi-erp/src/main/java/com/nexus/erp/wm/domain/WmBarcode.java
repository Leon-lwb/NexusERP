package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 条码实例对象 wm_barcode
 *
 * 说明：
 * - 记录系统已生成/已绑定的条码
 * - 后续可关联库存、批次、单据行、SN 等业务对象
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_barcode")
public class WmBarcode extends TenantEntity {

    /** 条码ID */
    @TableId(value = "barcode_id")
    private Long barcodeId;

    /** 条码值 */
    private String barcodeCode;

    /** 配置ID */
    private Long configId;

    /** 配置编码 */
    private String configCode;

    /** 业务类型 */
    private String bizType;

    /** 关联业务ID */
    private Long bizId;

    /** 关联业务行ID */
    private Long bizLineId;

    /** 物料ID */
    private Long materialId;

    /** 批次ID */
    private Long batchId;

    /** 库存ID */
    private Long stockId;

    /** 条码状态 */
    private String status;

    /** 生成时间 */
    private Date generateTime;

    /** 绑定时间 */
    private Date bindTime;

    /** 失效时间 */
    private Date expireTime;

    /** 备注 */
    private String remark;
}
