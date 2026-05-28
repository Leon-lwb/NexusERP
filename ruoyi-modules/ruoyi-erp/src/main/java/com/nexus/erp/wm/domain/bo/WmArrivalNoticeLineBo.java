package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;

import java.math.BigDecimal;

/**
 * 到货通知单行业务对象 wm_arrival_notice_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmArrivalNoticeLine.class, reverseConvertGenerate = false)
public class WmArrivalNoticeLineBo extends BaseEntity {

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

    private Long[] ids;
}
