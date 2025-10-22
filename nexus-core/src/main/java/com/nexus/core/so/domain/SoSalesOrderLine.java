package com.nexus.core.so.domain;

    import java.math.BigDecimal;
    import java.util.Date;
    import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 销售订单行对象 so_sales_order_line
 *
 * @author nexus
 * @date 2025-10-22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.so.mapper.SoSalesOrderLineMapper.SoSalesOrderLineResult")
public class SoSalesOrderLine extends BaseEntity {
private static final long serialVersionUID = 1L;

    /** 销售订单行表id */
        @TableId(value = "line_id", type = IdType.AUTO)
    private Long lineId;

    /** 销售订单ID */
            @Excel(name = "销售订单ID")
    private Long soId;

    /** 行号（0010,0020） */
            @Excel(name = "行号", readConverterExp = "0=010,0020")
    private String lineNo;

    /** 物料ID */
            @Excel(name = "物料ID")
    private Long materialId;

    /** 物料编码（冗余，提高查询性能） */
            @Excel(name = "物料编码", readConverterExp = "冗=余，提高查询性能")
    private String materialCode;

    /** 物料名称（冗余） */
            @Excel(name = "物料名称", readConverterExp = "冗=余")
    private String materialName;

    /** 物料规格（冗余） */
            @Excel(name = "物料规格", readConverterExp = "冗=余")
    private String materialSpec;

    /** 订单数量 */
            @Excel(name = "订单数量")
    private BigDecimal orderedQty;

    /** 已发货数量 */
            @Excel(name = "已发货数量")
    private BigDecimal deliveredQty;

    /** 单位 */
            @Excel(name = "单位")
    private String unit;

    /** 单价 */
            @Excel(name = "单价")
    private BigDecimal unitPrice;

    /** 行金额 = qty * price */
            @Excel(name = "行金额 = qty * price")
    private BigDecimal lineAmount;

    /** 税率 */
            @Excel(name = "税率")
    private BigDecimal taxRate;

    /** 行税额 */
            @Excel(name = "行税额")
    private BigDecimal taxAmount;

    /** 行折扣率 */
            @Excel(name = "行折扣率")
    private BigDecimal discountPercent;

    /** 行折扣额 */
            @Excel(name = "行折扣额")
    private BigDecimal discountAmount;

    /** 行要求交期 */
            @JsonFormat(pattern = "yyyy-MM-dd")
            @Excel(name = "行要求交期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date requestedDate;

    /** 行承诺交期 */
            @JsonFormat(pattern = "yyyy-MM-dd")
            @Excel(name = "行承诺交期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date promiseDate;

    /** 已履行数量（已发货） */
            @Excel(name = "已履行数量", readConverterExp = "已=发货")
    private BigDecimal fulfilledQty;

    /** 已投产数量 */
            @Excel(name = "已投产数量")
    private BigDecimal productionQty;

    /** 已采购数量 */
            @Excel(name = "已采购数量")
    private BigDecimal purchaseQty;

    /** 已预留库存数量 */
            @Excel(name = "已预留库存数量")
    private BigDecimal reservedQty;

    /** PENDING-待处理,SCHEDULED-已排产/已采购,PARTIALLY_FULFILLED-部分履行,FULFILLED-已履行,CANCELLED-已取消 */
            @Excel(name = "PENDING-待处理,SCHEDULED-已排产/已采购,PARTIALLY_FULFILLED-部分履行,FULFILLED-已履行,CANCELLED-已取消")
    private String lineStatus;

    /** 关联生产工单ID */
            @Excel(name = "关联生产工单ID")
    private Long linkedMoId;

    /** 关联采购订单ID */
            @Excel(name = "关联采购订单ID")
    private Long linkedPoId;

    /** 预留批次号 */
            @Excel(name = "预留批次号")
    private String reservedBatchNo;

    /** 预留序列号列表（JSON） */
            @Excel(name = "预留序列号列表", readConverterExp = "J=SON")
    private String reservedSerials;

    /** PA配套组ID（用于项目型订单配套发料） */
            @Excel(name = "PA配套组ID", readConverterExp = "用=于项目型订单配套发料")
    private Long paGroupId;

    /** 默认仓库ID */
            @Excel(name = "默认仓库ID")
    private Long warehouseId;

    /** 交付优先级 */
            @Excel(name = "交付优先级")
    private Long deliveryPriority;

    /** 客户行参考号 */
            @Excel(name = "客户行参考号")
    private String customerLineRef;

    /**  */
            @Excel(name = "")
    private Long sortOrder;

    /** 预留字段1 */
            @Excel(name = "预留字段1")
    private String attr1;

    /** 预留字段2 */
            @Excel(name = "预留字段2")
    private String attr2;

    /** 预留字段3 */
            @Excel(name = "预留字段3")
    private Long attr3;

    /** 预留字段4 */
            @Excel(name = "预留字段4")
    private Long attr4;

}