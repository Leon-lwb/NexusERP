package com.nexus.mes.tg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.tg.domain.TgTooling;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装台账视图对象 tg_tooling
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TgTooling.class)
public class TgToolingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工装ID */
    @ExcelProperty("工装ID")
    private Long toolingId;

    /** 工装编码 */
    @ExcelProperty("工装编码")
    private String toolingCode;

    /** 工装名称 */
    @ExcelProperty("工装名称")
    private String toolingName;

    /** 工装类型 */
    @ExcelProperty("工装类型")
    private String toolingType;

    /** 规格 */
    @ExcelProperty("规格")
    private String spec;

    /** 所属车间ID */
    private Long workshopId;

    /** 车间名称(冗余) */
    @ExcelProperty("车间")
    private String workshopName;

    /** 状态: IDLE/IN_USE/MAINTENANCE/SCRAPPED */
    @ExcelProperty("状态")
    private String status;

    /** 总寿命(次数) */
    @ExcelProperty("总寿命(次)")
    private Integer totalLifeCount;

    /** 总寿命(小时) */
    @ExcelProperty("总寿命(小时)")
    private BigDecimal totalLifeHours;

    /** 已使用次数 */
    @ExcelProperty("已用(次)")
    private Integer usedCount;

    /** 已使用时长 */
    @ExcelProperty("已用(小时)")
    private BigDecimal usedHours;

    /** 上次保养时间 */
    private Date lastMaintainTime;

    /** 采购日期 */
    private Date purchaseDate;

    /** 采购成本 */
    private BigDecimal purchaseCost;

    /** 备注 */
    private String remark;

    /** 剩余寿命(次数, 非库列, Service 填充 = totalLifeCount - usedCount) */
    private Integer remainingCount;

    /** 剩余寿命(小时, 非库列, Service 填充 = totalLifeHours - usedHours) */
    private BigDecimal remainingHours;
}
