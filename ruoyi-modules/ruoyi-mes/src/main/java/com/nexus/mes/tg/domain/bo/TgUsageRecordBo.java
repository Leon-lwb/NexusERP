package com.nexus.mes.tg.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.tg.domain.TgUsageRecord;

import java.math.BigDecimal;

/**
 * MES工装使用/领用记录业务对象 tg_usage_record
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = TgUsageRecord.class, reverseConvertGenerate = false)
public class TgUsageRecordBo extends BaseEntity {

    /** 使用记录ID */
    private Long usageId;

    /** 工装ID */
    @NotNull(message = "工装ID不能为空")
    private Long toolingId;

    /** 工单ID */
    private Long woId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 工位ID */
    private Long workstationId;

    /** 工位名称(冗余) */
    private String workstationName;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名(冗余) */
    private String operatorName;

    /** 使用次数 */
    private Integer usageCount;

    /** 使用时长 */
    private BigDecimal usageHours;

    /** 状态(查询用): OUT/RETURNED */
    private String status;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
