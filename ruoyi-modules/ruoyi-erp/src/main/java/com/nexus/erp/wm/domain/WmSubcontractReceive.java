package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外收货单对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_subcontract_receive")
public class WmSubcontractReceive extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 收货单ID */
    Long receiveId;
    /** 收货单号 */
    String receiveNo;
    /** 委外订单ID */
    Long subcontractId;
    /** 委外单号(冗余) */
    String subcontractNo;
    /** 产出物料ID */
    Long materialId;
    /** 物料编码 */
    String materialCode;
    /** 物料名称 */
    String materialName;
    /** 规格型号 */
    String materialSpec;
    /** 单位名称 */
    String baseUomName;
    /** 供应商ID */
    Long vendorId;
    /** 供应商编码 */
    String vendorCode;
    /** 供应商名称 */
    String vendorName;
    /** 入库仓库ID */
    Long targetWarehouseId;
    /** 入库仓库编码 */
    String targetWarehouseCode;
    /** 入库仓库名称 */
    String targetWarehouseName;
    /** 入库库位ID */
    Long targetLocationId;
    /** 收货数量 */
    BigDecimal receivedQty;
    /** 外协批次号 */
    String vendorBatchCode;
    /** SN范围记录 */
    String snScope;
    /** 收货日期 */
    Date receiveDate;
    /** 状态: DRAFT/POSTED */
    String status;
    /** 收货人 */
    String receiverName;
    /** 创建部门 */
    Long createDept;
    /** 创建者 */
    Long createBy;
    /** 创建时间 */
    Date createTime;
    /** 更新者 */
    Long updateBy;
    /** 更新时间 */
    Date updateTime;
    /** 备注 */
    String remark;
}
