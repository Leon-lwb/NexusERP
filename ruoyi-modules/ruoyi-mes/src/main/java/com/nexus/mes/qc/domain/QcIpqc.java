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
 * MES过程检验对象 qc_ipqc
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_ipqc")
public class QcIpqc extends TenantEntity {

    /** IPQC ID(主键) */
    @TableId(value = "ipqc_id")
    private Long ipqcId;

    /** IPQC编号 */
    private String ipqcNo;

    /** MES工单ID */
    private Long mesWoId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 流转卡ID */
    private Long cardId;

    /** 流转卡工序记录ID */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 抽样数量 */
    private BigDecimal sampleQty;

    /** 不良数量 */
    private BigDecimal defectQty;

    /** 不良率(%) */
    private BigDecimal defectRate;

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
