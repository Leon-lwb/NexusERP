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
 * 固定资产类别 fa_asset_category(折旧政策)
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("fa_asset_category")
public class FaAssetCategory extends TenantEntity {

    /** 类别ID */
    @TableId(value = "category_id")
    private Long categoryId;

    /** 类别编码 */
    private String categoryCode;

    /** 类别名称(如 机器设备/办公设备/运输工具) */
    private String categoryName;

    /** 折旧方法 STRAIGHT_LINE-直线法 DOUBLE_DECLINING-双倍余额递减 SUM_OF_YEARS-年数总和 MANUAL-手工 */
    private String depreciationMethod;

    /** 预计使用年限(月) */
    private Integer usefulLife;

    /** 净残值率(0-1,默认0) */
    private BigDecimal salvageValueRate;

    /** 折旧费用科目ID(借,默认查 6602 管理费用) */
    private Long debitAccountId;

    /** 累计折旧科目ID(贷,默认查 1602) */
    private Long creditAccountId;

    /** 处置损失科目ID(借,默认查 6711 营业外支出) */
    private Long disposalLossAccountId;

    /** 处置收益科目ID(贷,默认查 6301 营业外收入) */
    private Long disposalGainAccountId;

    /** 状态 0-正常 1-停用 */
    private String status;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
