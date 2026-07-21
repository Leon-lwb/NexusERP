package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存调拨单业务对象 wm_transfer
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmTransfer.class, reverseConvertGenerate = false)
public class WmTransferBo extends BaseEntity {

    /**
     * 调拨单ID
     */
    private Long transferId;

    /**
     * 调拨单号
     */
    private String transferNo;

    /**
     * 调出仓库ID
     */
    private Long fromWarehouseId;

    /**
     * 调出仓库编码
     */
    private String fromWarehouseCode;

    /**
     * 调出仓库名称
     */
    private String fromWarehouseName;

    /**
     * 调出库区ID
     */
    private Long fromAreaId;

    /**
     * 调出库区编码
     */
    private String fromAreaCode;

    /**
     * 调出库区名称
     */
    private String fromAreaName;

    /**
     * 调入仓库ID
     */
    private Long toWarehouseId;

    /**
     * 调入仓库编码
     */
    private String toWarehouseCode;

    /**
     * 调入仓库名称
     */
    private String toWarehouseName;

    /**
     * 调入库区ID
     */
    private Long toAreaId;

    /**
     * 调入库区编码
     */
    private String toAreaCode;

    /**
     * 调入库区名称
     */
    private String toAreaName;

    /**
     * 调拨类型
     */
    private String transferType;

    /**
     * 调拨总数量
     */
    private BigDecimal totalQuantity;

    /**
     * 单据状态
     */
    private String status;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;

    /**
     * 调拨日期
     */
    private Date transferDate;

    /**
     * 执行日期
     */
    private Date executeDate;

    /**
     * 关联流程ID
     */
    private Long selectFlowId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    private String delFlag;

    /**
     * 调拨单行项目列表
     */
    private List<WmTransferLine> wmTransferLineList;
}
