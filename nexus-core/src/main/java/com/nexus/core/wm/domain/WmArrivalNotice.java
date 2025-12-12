package com.nexus.core.wm.domain;

import java.util.List;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 到货通知对象 wm_arrival_notice
 *
 * @author nexus
 * @date 2025-12-04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmArrivalNoticeMapper.WmArrivalNoticeResult")
public class WmArrivalNotice extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 通知单ID */
    @TableId(value = "notice_id",type = IdType.AUTO)
    private Long noticeId;
    /** 通知单编号 */
    @Excel(name = "通知单编号")
    private String noticeCode;
    /** 通知单名称 */
    @Excel(name = "通知单名称")
    private String noticeName;
    /** 采购订单编号 */
    @Excel(name = "采购订单编号")
    private String purOrderCode;
    /** 供应商ID */
    @Excel(name = "供应商ID")
    private Long vendorId;
    /** 供应商编码 */
    @Excel(name = "供应商编码")
    private String vendorCode;
    /** 供应商名称 */
    @Excel(name = "供应商名称")
    private String vendorName;
    /** 供应商简称 */
    @Excel(name = "供应商简称")
    private String vendorNick;
    /** 到货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "到货日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date arrivalDate;
    /** 联系人 */
    @Excel(name = "联系人")
    private String contact;
    /** 联系方式 */
    @Excel(name = "联系方式")
    private String phone;
    /** 单据状态 */
    @Excel(name = "单据状态")
    private String status;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
    /** 到货通知行列表 */
    private List<WmArrivalNoticeLine> wmArrivalNoticeLineList;
}