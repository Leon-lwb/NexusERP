package com.nexus.erp.pu.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;

import java.util.Date;
import java.util.List;

/**
 * 采购申请视图对象 VO
 * <p>
 * 用于采购申请的前端展示和导出，通过 {@link ExcelProperty} 注解定义导出列映射。
 * 与 {@link PuPurchaseRequisition} 实体通过 MapStruct 自动映射转换。
 * 包含申请头展示信息和明细行列表 {@link #puPurchaseRequisitionLineList}。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuPurchaseRequisition.class)
public class PuPurchaseRequisitionVo extends BaseEntity {

    @ExcelProperty(value = "采购申请ID")
    private Long requisitionId;

    @ExcelProperty(value = "申请单号")
    private String requisitionNo;

    @ExcelProperty(value = "申请名称")
    private String requisitionName;

    @ExcelProperty(value = "申请人")
    private String applicantName;

    @ExcelProperty(value = "申请日期")
    private Date requisitionDate;

    @ExcelProperty(value = "需求日期")
    private Date requiredDate;

    @ExcelProperty(value = "来源类型")
    private String sourceType;

    @ExcelProperty(value = "状态")
    private String status;

    /** 申请人ID */
    private Long applicantId;

    /** 选用的审批流ID */
    private Long selectFlowId;

    /** 备注 */
    private String remark;

    /** 扩展字段1 */
    private String attr1;

    /** 扩展字段2 */
    private String attr2;

    /** 扩展字段3 */
    private Long attr3;

    /** 扩展字段4 */
    private Long attr4;

    /** 采购申请明细行列表 */
    private List<PuPurchaseRequisitionLine> puPurchaseRequisitionLineList;
}
