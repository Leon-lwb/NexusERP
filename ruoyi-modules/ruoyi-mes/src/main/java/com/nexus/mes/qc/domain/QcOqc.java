package com.nexus.mes.qc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES出货检验对象 qc_oqc
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_oqc")
public class QcOqc extends TenantEntity {

    /** OQC ID(主键) */
    @TableId(value = "oqc_id")
    private Long oqcId;

    /** OQC编号 */
    private String oqcNo;

    /** ERP销售订单ID */
    private Long soId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 检验数量 */
    private BigDecimal quantity;

    /** 抽样数量 */
    private BigDecimal sampleQty;

    /** 检验结果: QUALIFIED/UNQUALIFIED/PENDING */
    private String inspectResult;

    /** 不良品信息(JSON: 类型/数量/描述) */
    private String defectInfo;

    /** 检验员ID */
    private Long inspectorId;

    /** 检验员姓名 */
    private String inspectorName;

    /** 检验时间 */
    private Date inspectTime;

    /** 状态: PENDING/INSPECTING/COMPLETED */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
