
package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.time.LocalDateTime;

/**
 * 到货通知对象 wm_arrival_notice vo
 *
 * @author nexus
 * @date 2025-12-04
 */
@Data
public class WmArrivalNoticeVo extends BasePageDtoRequest {

    /**
     * 通知单ID
     */
    private Long noticeId;
    /**
     * 通知单编号
     */
    private String noticeCode;
    /**
     * 通知单名称
     */
    private String noticeName;
    /**
     * 采购订单编号
     */
    private String purOrderCode;
    /**
     * 供应商ID
     */
    private Long vendorId;
    /**
     * 供应商编码
     */
    private String vendorCode;
    /**
     * 供应商名称
     */
    private String vendorName;
    /**
     * 供应商简称
     */
    private String vendorNick;
    /**
     * 到货日期
     */
    private LocalDateTime arrivalDate;
    /**
     * 联系人
     */
    private String contact;
    /**
     * 联系方式
     */
    private String phone;
    /**
     * 单据状态
     */
    private String status;
    /**
     * 备注
     */
    private String remark;

}