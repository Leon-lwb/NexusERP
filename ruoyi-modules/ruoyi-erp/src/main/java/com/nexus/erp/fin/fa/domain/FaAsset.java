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
 * 固定资产卡片 fa_asset
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("fa_asset")
public class FaAsset extends TenantEntity {

    /** 资产ID */
    @TableId(value = "asset_id")
    private Long assetId;

    /** 资产编号(自动生成 FA-YYYY-NNNN) */
    private String assetNo;

    /** 资产名称 */
    private String assetName;

    /** 类别ID */
    private Long categoryId;

    /** 原值 */
    private BigDecimal originalValue;

    /** 净残值(原值×残值率,可手工覆盖) */
    private BigDecimal salvageValue;

    /** 使用年限(月,随类别带出可改) */
    private Integer usefulLife;

    /** 折旧方法(空=随类别) */
    private String depreciationMethod;

    /** 购置日期 */
    private Date acquisitionDate;

    /** 启用日期(开始计提,空=购置日) */
    private Date inUseDate;

    /** 存放地点 */
    private String location;

    /** 状态 IN_USE-使用中 CWIP-在建 DISPOSED-已处置 */
    private String status;

    /** 累计已计提折旧(随折旧过账更新) */
    private BigDecimal cumulativeDepreciation;

    /** 账面净值(原值-累计折旧) */
    private BigDecimal netBookValue;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
