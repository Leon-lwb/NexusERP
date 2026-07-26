package com.nexus.mes.tg.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.tg.domain.TgTooling;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装台账业务对象 tg_tooling
 * <p>承载 工装台账 的增改入参与查询条件。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = TgTooling.class, reverseConvertGenerate = false)
public class TgToolingBo extends BaseEntity {

    /** 工装ID */
    private Long toolingId;

    /** 工装编码(留空则自动生成 MES_TOOLING_CODE) */
    private String toolingCode;

    /** 工装名称 */
    @NotBlank(message = "工装名称不能为空")
    private String toolingName;

    /** 工装类型 */
    @NotBlank(message = "工装类型不能为空")
    private String toolingType;

    /** 规格 */
    private String spec;

    /** 所属车间ID */
    private Long workshopId;

    /** 车间名称(冗余) */
    private String workshopName;

    /** 状态(查询用,新增默认 IDLE) */
    private String status;

    /** 总寿命(次数) */
    private Integer totalLifeCount;

    /** 总寿命(小时) */
    private BigDecimal totalLifeHours;

    /** 采购日期 */
    private Date purchaseDate;

    /** 采购成本 */
    private BigDecimal purchaseCost;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
