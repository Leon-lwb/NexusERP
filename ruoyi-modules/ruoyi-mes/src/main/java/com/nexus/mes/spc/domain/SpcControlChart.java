package com.nexus.mes.spc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES SPC控制图对象 spc_control_chart
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spc_control_chart")
public class SpcControlChart extends TenantEntity {

    /** 控制图ID(主键) */
    @TableId(value = "chart_id")
    private Long chartId;

    /** 控制图名称 */
    private String chartName;

    /** 控制图类型: X_BAR_R/X_BAR_S/P/C/U */
    private String chartType;

    /** 关联采集点ID */
    private Long pointId;

    /** 关联物料ID */
    private Long materialId;

    /** 关联工序ID */
    private Long processId;

    /** 上控制限(analyze计算回写) */
    private BigDecimal ucl;

    /** 中心线 */
    private BigDecimal cl;

    /** 下控制限 */
    private BigDecimal lcl;

    /** 规格上限(USL, 用户维护, 计量型图适用) */
    private BigDecimal usl;

    /** 规格下限(LSL, 用户维护) */
    private BigDecimal lsl;

    /** 过程能力指数Cp(analyze回写) */
    private BigDecimal cp;

    /** 过程能力指数Cpk(analyze回写) */
    private BigDecimal cpk;

    /** 能力等级: A+(>=1.67)/A(>=1.33)/B(>=1.0)/C(<1.0) */
    private String cpkGrade;

    /** 样本大小 */
    private Integer sampleSize;

    /** 状态: ACTIVE/INACTIVE */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
