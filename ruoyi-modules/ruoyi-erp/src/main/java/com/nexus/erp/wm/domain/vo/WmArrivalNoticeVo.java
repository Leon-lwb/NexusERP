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

    private Long noticeId;

    @ExcelProperty(value = "通知单编号")
    private String noticeCode;

    @ExcelProperty(value = "通知单名称")
    private String noticeName;

    @ExcelProperty(value = "采购订单编号")
    private String purOrderCode;

    private Long vendorId;

    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    @ExcelProperty(value = "供应商简称")
    private String vendorNick;

    @ExcelProperty(value = "到货日期")
    private Date arrivalDate;

    @ExcelProperty(value = "联系人")
    private String contact;

    @ExcelProperty(value = "联系方式")
    private String phone;

    @ExcelProperty(value = "单据状态")
    private String status;

    private Long selectFlowId;

    private String remark;

    private List<WmArrivalNoticeLineVo> wmArrivalNoticeLineList;
}
