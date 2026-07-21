package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmArrivalNotice;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 到货通知视图对象 wm_arrival_notice
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmArrivalNotice.class)
public class WmArrivalNoticeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 到货通知ID
     */
    private Long noticeId;

    /**
     * 通知单编号
     */
    @ExcelProperty(value = "通知单编号")
    private String noticeCode;

    /**
     * 通知单名称
     */
    @ExcelProperty(value = "通知单名称")
    private String noticeName;

    /**
     * 采购订单编号
     */
    @ExcelProperty(value = "采购订单编号")
    private String purOrderCode;

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
     */
    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    /**
     * 供应商简称
     */
    @ExcelProperty(value = "供应商简称")
    private String vendorNick;

    /**
     * 到货日期
     */
    @ExcelProperty(value = "到货日期")
    private Date arrivalDate;

    /**
     * 联系人
     */
    @ExcelProperty(value = "联系人")
    private String contact;

    /**
     * 联系方式
     */
    @ExcelProperty(value = "联系方式")
    private String phone;

    /**
     * 单据状态
     */
    @ExcelProperty(value = "单据状态")
    private String status;

    /**
     * 关联流程ID
     */
    private Long selectFlowId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 到货通知明细列表
     */
    private List<WmArrivalNoticeLineVo> wmArrivalNoticeLineList;
}
