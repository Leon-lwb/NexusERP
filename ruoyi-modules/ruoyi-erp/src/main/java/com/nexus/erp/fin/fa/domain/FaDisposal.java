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
import java.util.Date;

/**
 * 固定资产处置记录 fa_disposal
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("fa_disposal")
public class FaDisposal extends TenantEntity {

    /** 处置ID */
    @TableId(value = "disposal_id")
    private Long disposalId;

    /** 资产ID */
    private Long assetId;

    /** 类型 SALE-出售 SCRAP-报废 */
    private String disposalType;

    /** 处置日期 */
    private Date disposalDate;

    /** 处置收入(售价/残值回收) */
    private BigDecimal disposalPrice;

    /** 处置时账面净值(原值-累计折旧) */
    private BigDecimal netBookValue;

    /** 处置损益(正=收益 负=损失) */
    private BigDecimal gainLoss;

    /** 过账凭证ID */
    private Long postedVoucherId;

    /** 过账凭证号 */
    private String postedVoucherNo;

    /** 状态 POSTED-已过账 */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
