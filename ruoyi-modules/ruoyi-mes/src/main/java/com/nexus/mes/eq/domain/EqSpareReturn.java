package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import com.nexus.mes.eq.service.IEqSparePartService;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES备件退库单头对象 eq_spare_return（MES-EQ-04 收口 / 审计 §4 第 7 项）
 *
 * <p>退库 = 备件从现场回到仓库（ERP 侧按<b>入库方向</b>过账）。单据生命周期： {@code DRAFT}（草稿，可改可删）→ {@code CONFIRMED}（已确认，逐行调
 * {@link IEqSparePartService#stockTransaction} 以 {@code TXN_RETURN} 过账）； 另有 {@code CANCELLED} 终态。
 *
 * <p>确认用 CAS（{@code status = 'DRAFT'} 条件更新）保证并发/重复确认只过账一次，明细行见 {@link EqSpareReturnLine}。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_spare_return")
public class EqSpareReturn extends TenantEntity {

    /** 退库单ID(主键) */
    @TableId(value = "return_id")
    private Long returnId;

    /** 退库单号 MESRT + yyyyMMdd + 4位当日序号 */
    private String returnNo;

    /** 退库目标 ERP 仓库ID(wm_warehouse.warehouse_id) */
    private Long warehouseId;

    /** 退库日期 */
    private Date returnDate;

    /** 退库类型 REPAIR_RETURN / EXCESS_RETURN / OTHER */
    private String returnType;

    /** 状态 DRAFT / CONFIRMED / CANCELLED */
    private String status;

    /** 明细数量合计 */
    private BigDecimal totalQty;

    /** 备注 */
    private String remark;

    /** 确认人ID */
    private Long confirmBy;

    /** 确认时间 */
    private Date confirmTime;

    /** 版本号(确认时 CAS 自增, 供并发控制与审计) */
    private Integer version;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
