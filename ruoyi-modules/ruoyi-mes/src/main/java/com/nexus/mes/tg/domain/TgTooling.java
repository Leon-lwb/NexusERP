package com.nexus.mes.tg.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装台账对象 tg_tooling
 * <p>属于 MES 工装管理模块，表达 工装/模治具 的领域模型。</p>
 * <p>状态机: IDLE ⇄ IN_USE(领用/归还)
 *                 └-> MAINTENANCE -> IDLE(保养归零)
 *                 └-> SCRAPPED(终态)</p>
 * <p>寿命双轨: used_count(次数) + used_hours(时长), 归还累加, 保养归零</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tg_tooling")
public class TgTooling extends TenantEntity {

    /** 工装ID(主键) */
    @TableId(value = "tooling_id")
    private Long toolingId;

    /** 工装编码 */
    private String toolingCode;

    /** 工装名称 */
    private String toolingName;

    /** 工装类型: MOLD/FIXTURE/CUTTER/GAUGE/OTHER */
    private String toolingType;

    /** 规格 */
    private String spec;

    /** 所属车间ID */
    private Long workshopId;

    /** 车间名称(冗余) */
    private String workshopName;

    /** 状态: IDLE/IN_USE/CALIBRATING/MAINTENANCE/SCRAPPED */
    private String status;

    /** 总寿命(次数) */
    private Integer totalLifeCount;

    /** 总寿命(小时) */
    private BigDecimal totalLifeHours;

    /** 已使用次数 */
    private Integer usedCount;

    /** 已使用时长 */
    private BigDecimal usedHours;

    /** 上次保养时间 */
    private Date lastMaintainTime;

    /** 校准周期(天), 空=未纳入校准管理 (MES-TG-01) */
    private Integer calibrationCycleDays;

    /** 上次校准日期 (MES-TG-01) */
    private Date lastCalibrationDate;

    /** 下次校准日期, 到期/超期即拦截领用与进站 (MES-TG-01) */
    private Date nextCalibrationDate;

    /** 校准人ID (MES-TG-01) */
    private Long calibratedBy;

    /** 校准结果: PASS/FAIL/ADJUSTED (MES-TG-01) */
    private String calibrationResult;

    /** 校准备注 (MES-TG-01) */
    private String calibrationRemark;

    /** 采购日期 */
    private Date purchaseDate;

    /** 采购成本 */
    private BigDecimal purchaseCost;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
