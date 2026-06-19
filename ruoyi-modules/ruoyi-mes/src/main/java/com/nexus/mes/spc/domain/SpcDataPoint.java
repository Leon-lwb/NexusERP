package com.nexus.mes.spc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES SPC数据点对象 spc_data_point
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spc_data_point")
public class SpcDataPoint extends TenantEntity {

    /** 数据点ID(主键) */
    @TableId(value = "data_id")
    private Long dataId;

    /** 控制图ID */
    private Long chartId;

    /** 样本组号 */
    private Integer sampleGroup;

    /** 组内序号 */
    private Integer sampleIndex;

    /** 值(组均值/不良率/缺陷数等) */
    private BigDecimal value;

    /** 是否超控制限(0/1) */
    private String isOoc;

    /** 违规规则(RULE1/2/3/4) */
    private String oocRule;

    /** 采集时间 */
    private Date collectTime;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
