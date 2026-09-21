package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES备件 → ERP 仓储过账同步台账 eq_spare_erp_sync（MES-EQ-04）
 * <p>
 * 每笔备件出入库在同一事务内写一行同步台账（业务幂等键 {@link #bizKey} = {@code EQSP:<tenantId>:<logId>}），
 * 事务提交后再发 MQ 到 {@code QueueConstants.MES_SPARE_ERP_QUEUE}，由 ERP 侧
 * {@code WmSpareStockPostingServiceImpl} 过账到 {@code wm_material_stock}。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>幂等</b>：{@code uk_tenant_biz_key(tenant_id, biz_key)} 唯一约束 + ERP 侧消费幂等（Redis 原子占位 + 流水查重）；</li>
 *   <li><b>失败可见</b>：投递失败写 {@link #syncStatus} = {@code FAILED} 与 {@link #lastError}，
 *       并提供 {@code /mes/eq/spare-part/erp-sync/failed} 列表与重投端点，绝不静默；</li>
 *   <li><b>对账</b>：ERP 过账成功的判据是 {@code wm_material_stock_tx} 存在
 *       {@code source_doc_type='MES_SPARE' AND source_doc_code=bizKey} 的流水行。</li>
 * </ul>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_spare_erp_sync")
public class EqSpareErpSync extends TenantEntity {

    /** 同步台账ID(主键) */
    @TableId(value = "sync_id")
    private Long syncId;

    /** 业务幂等键 EQSP:<tenantId>:<logId> */
    private String bizKey;

    /** 备件ID */
    private Long spareId;

    /** 备件编码(冗余) */
    private String spareCode;

    /** ERP物料ID */
    private Long materialId;

    /** ERP仓库ID */
    private Long warehouseId;

    /** 变动类型 IN/OUT/REPAIR_USE/RETURN */
    private String transactionType;

    /** 变动数量(正数) */
    private BigDecimal quantity;

    /** 关联单据类型 */
    private String refType;

    /** 关联单据ID */
    private Long refId;

    /** 关联的 MES 库存流水ID(eq_spare_stock_log.log_id) */
    private Long logId;

    /** 同步状态 PENDING/SENT/SYNCED/FAILED */
    private String syncStatus;

    /** 重投次数 */
    private Integer retryCount;

    /** 最近一次失败原因 */
    private String lastError;

    /** 最近一次投递时间 */
    private Date lastPublishTime;

    /** ERP 过账后的可用量快照(在库 − 已预留, frozen_flag=0) */
    private BigDecimal erpAvailableAfter;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
