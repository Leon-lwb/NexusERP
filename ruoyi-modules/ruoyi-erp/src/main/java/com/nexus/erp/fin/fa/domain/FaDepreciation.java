package com.nexus.erp.fin.fa.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 固定资产折旧记录 fa_depreciation
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("fa_depreciation")
public class FaDepreciation extends TenantEntity {

    /** 折旧ID */
    @TableId(value = "depreciation_id")
    private Long depreciationId;

    /** 资产ID */
    private Long assetId;

    /** 期间ID */
    private Long periodId;

    /** 本期折旧额 */
    private BigDecimal depreciationAmount;

    /** 计提后累计折旧 */
    private BigDecimal cumulativeDepreciation;

    /** 状态 PLANNED-计划 DONE-已过账 */
    private String status;

    /** 过账凭证ID */
    private Long postedVoucherId;

    /** 过账凭证号 */
    private String postedVoucherNo;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
