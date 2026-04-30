package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

/**
 * 到货通知对象 wm_arrival_notice
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_arrival_notice")
public class WmArrivalNotice extends TenantEntity {

    @TableId(value = "notice_id")
    private Long noticeId;

    private String noticeCode;
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

    @TableField(exist = false)
    private List<WmArrivalNoticeLine> wmArrivalNoticeLineList;

    @TableLogic
    private String delFlag;
}
