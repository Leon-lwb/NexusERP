package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 到货通知单行对象 wm_arrival_notice_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_arrival_notice_line")
public class WmArrivalNoticeLine extends TenantEntity {

    @TableId(value = "line_id")
    private Long lineId;

    private Long noticeId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomId;
    private String baseUomName;
    private BigDecimal quantityArrival;
    private BigDecimal quantityQuanlified;
    private String iqcCheck;
    private Long iqcId;
    private String iqcCode;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableLogic
    private String delFlag;
}
