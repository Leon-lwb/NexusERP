package com.nexus.mes.tg.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * MES工装校准登记业务对象 (MES-TG-01)
 * <p>承载「登记校准」端点的入参: 校准结论 + 校准日期 + 校准周期。</p>
 * <p>下次校准日由服务端计算(校准日期 + 周期), 不接受客户端直传, 避免人为把
 * 到期日改到未来从而绕过超期拦截。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TgCalibrationBo extends BaseEntity {

    /** 校准周期(天), 留空则沿用工装台账上的 calibration_cycle_days */
    private Integer calibrationCycleDays;

    /** 校准日期, 留空取当前时间 */
    private Date calibrationDate;

    /** 校准结果: PASS(默认)/FAIL/ADJUSTED */
    private String calibrationResult;

    /** 校准人ID, 留空取当前登录人 */
    private Long calibratedBy;

    /** 校准备注 */
    private String calibrationRemark;

    /** 工装备注(可选, 非空时一并更新) */
    private String remark;
}
