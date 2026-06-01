package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

/**
 * 到货通知对象 wm_arrival_notice
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_arrival_notice")
public class WmArrivalNotice extends TenantEntity {

    /** 通知单ID */
    @TableId(value = "notice_id")
    private Long noticeId;

    /** 通知单编号 */
    private String noticeCode;
    /** 通知单名称 */
    private String noticeName;
    /** 采购订单编号 */
    private String purOrderCode;
    /** 供应商ID */
    private Long vendorId;
    /** 供应商编码 */
    private String vendorCode;
    /** 供应商名称 */
    private String vendorName;
    /** 供应商简称 */
    private String vendorNick;
    /** 到货日期 */
    private Date arrivalDate;
    /** 联系人 */
    private String contact;
    /** 联系方式 */
    private String phone;
    /** 单据状态 */
    private String status;
    /** 备注 */
    private String remark;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;

    /** 到货通知单行列表 */
    @TableField(exist = false)
    private List<WmArrivalNoticeLine> wmArrivalNoticeLineList;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
