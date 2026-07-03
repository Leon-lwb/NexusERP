package com.nexus.erp.po.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.po.domain.PoRequisition;
import com.nexus.erp.po.domain.PoRequisitionDetail;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 部门请购单视图对象 po_requisition
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PoRequisition.class)
public class PoRequisitionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 请购单ID
     */
    @ExcelProperty(value = "请购单ID")
    private Long requisitionId;

    /**
     * 请购单号
     */
    @ExcelProperty(value = "请购单号")
    private String requisitionNo;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 部门ID（关联sys_dept）
     */
    private Long deptId;

    /**
     * 部门名称
     */
    @ExcelProperty(value = "部门")
    private String deptName;

    /**
     * 申请人ID（关联sys_user）
     */
    private Long applicantId;

    /**
     * 申请人
     */
    @ExcelProperty(value = "申请人")
    private String applicantName;

    /**
     * 需求日期
     */
    @ExcelProperty(value = "需求日期")
    private Date requiredDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 请购单明细列表
     */
    private List<PoRequisitionDetail> poRequisitionDetailList;
}
