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
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmArrivalNotice.class, reverseConvertGenerate = false)
public class WmArrivalNoticeBo extends BaseEntity {

    private Long noticeId;

    @NotBlank(message = "通知单编号不能为空")
    private String noticeCode;

    @NotBlank(message = "通知单名称不能为空")
    private String noticeName;

    private String purOrderCode;
    private Long vendorId;
    private String vendorCode;
    private String vendorName;
    private String vendorNick;
    private Date arrivalDate;
    private String contact;
    private String phone;
    private String status;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    private List<WmArrivalNoticeLineBo> wmArrivalNoticeLineList;

    private Long[] ids;
}
