package com.nexus.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoSalesOrderLine;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售订单行业务对象 so_sales_order_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoSalesOrderLine.class, reverseConvertGenerate = false)
public class SoSalesOrderLineBo extends BaseEntity {

    /** 行ID */
    private Long lineId;

    /** 销售订单ID */
    private Long soId;
    /** 行号 */
    private String lineNo;
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 物料规格 */
    private String materialSpec;
    /** 订单数量 */
    private BigDecimal orderedQty;
    /** 已发货数量 */
    private BigDecimal deliveredQty;
    /** 单位 */
    private String unit;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 行金额 */
    private BigDecimal lineAmount;
    /** 税率 */
    private BigDecimal taxRate;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 折扣百分比 */
    private BigDecimal discountPercent;
    /** 折扣金额 */
    private BigDecimal discountAmount;
    /** 要求交货日期 */
    private Date requestedDate;
    /** 承诺交货日期 */
    private Date promiseDate;
    /** 已履行数量 */
    private BigDecimal fulfilledQty;
    /** 已投产数量 */
    private BigDecimal productionQty;
    /** 已采购数量 */
    private BigDecimal purchaseQty;
    /** 已预留数量 */
    private BigDecimal reservedQty;
    /** 行状态 */
    private String lineStatus;
    /** 关联生产订单ID */
    private Long linkedMoId;
    /** 关联采购订单ID */
    private Long linkedPoId;
    /** 预留批次号 */
    private String reservedBatchNo;
    /** 预留序列号 */
    private String reservedSerials;
    /** 配套组ID */
    private Long paGroupId;
    /** 仓库ID */
    private Long warehouseId;
    /** 交货优先级 */
    private Long deliveryPriority;
    /** 客户行参考 */
    private String customerLineRef;
    /** 排序号 */
    private Long sortOrder;
    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private Long attr3;
    /** 扩展字段4 */
    private Long attr4;
    /** 备注 */
    private String remark;

    /** ID数组（用于批量操作） */
    private Long[] ids;
}
