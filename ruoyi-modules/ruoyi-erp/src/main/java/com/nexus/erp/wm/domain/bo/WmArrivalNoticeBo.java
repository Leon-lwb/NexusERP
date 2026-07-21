package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmArrivalNotice;

import java.util.Date;
import java.util.List;

/**
 * 到货通知业务对象 wm_arrival_notice
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmArrivalNotice.class, reverseConvertGenerate = false)
public class WmArrivalNoticeBo extends BaseEntity {

    /**
     * 到货通知ID
     */
    private Long noticeId;

    /**
     * 到货通知编码
     */
    private String noticeCode;

    /**
     * 通知单名称
     */
    @NotBlank(message = "通知单名称不能为空")
    private String noticeName;

    /**
     * 采购订单号
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
    private Date arrivalDate;

    /**
     * 联系人
     */
    private String contact;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 状态
     */
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
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 到货通知明细列表
     */
    private List<WmArrivalNoticeLineBo> wmArrivalNoticeLineList;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}
