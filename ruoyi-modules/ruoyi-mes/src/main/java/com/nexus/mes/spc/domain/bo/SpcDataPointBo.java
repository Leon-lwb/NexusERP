package com.nexus.mes.spc.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.spc.domain.SpcDataPoint;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES SPC数据点业务对象 spc_data_point
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SpcDataPoint.class, reverseConvertGenerate = false)
public class SpcDataPointBo extends BaseEntity {

    /** 数据点ID */
    private Long dataId;

    /** 控制图ID */
    private Long chartId;

    /** 样本组号 */
    private Integer sampleGroup;

    /** 组内序号 */
    private Integer sampleIndex;

    /** 值 */
    private BigDecimal value;

    /** 组内离散度: 极差R/标准差S/移动极差MR */
    private BigDecimal subValue;

    /** 是否超控制限 */
    private String isOoc;

    /** 违规规则 */
    private String oocRule;

    /** 采集时间 */
    private Date collectTime;

    /** 批量删除ID集合 */
    private Long[] ids;
}
