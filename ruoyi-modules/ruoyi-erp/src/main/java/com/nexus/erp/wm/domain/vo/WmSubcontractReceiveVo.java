package com.nexus.erp.wm.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmSubcontractReceive;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外收货单视图对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmSubcontractReceive.class)
public class WmSubcontractReceiveVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 收货单ID */
    private Long receiveId;
    /** 收货单号 */
    private String receiveNo;
    /** 委外订单ID */
    private Long subcontractId;
    /** 委外单号(冗余) */
    private String subcontractNo;
    /** 产出物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 单位名称 */
    private String baseUomName;
    /** 供应商ID */
    private Long vendorId;
    /** 供应商编码 */
    private String vendorCode;
    /** 供应商名称 */
    private String vendorName;
    /** 入库仓库ID */
    private Long targetWarehouseId;
    /** 入库仓库编码 */
    private String targetWarehouseCode;
    /** 入库仓库名称 */
    private String targetWarehouseName;
    /** 入库库位ID */
    private Long targetLocationId;
    /** 收货数量 */
    private BigDecimal receivedQty;
    /** 外协批次号 */
    private String vendorBatchCode;
    /** SN范围记录 */
    private String snScope;
    /** 收货日期 */
    private Date receiveDate;
    /** 状态: DRAFT/POSTED */
    private String status;
    /** 收货人 */
    private String receiverName;
    /** 创建部门 */
    private Long createDept;
    /** 创建者 */
    private Long createBy;
    /** 创建时间 */
    private Date createTime;
    /** 更新者 */
    private Long updateBy;
    /** 更新时间 */
    private Date updateTime;
    /** 备注 */
    private String remark;
}
