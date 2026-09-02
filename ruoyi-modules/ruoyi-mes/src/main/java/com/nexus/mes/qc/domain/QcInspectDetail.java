package com.nexus.mes.qc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * QC检验单明细
 * <p>检验单创建时按检验模板快照生成, 录入实测值后由判定引擎回写 judgeResult。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspect_detail")
public class QcInspectDetail extends TenantEntity {
    @TableId(value = "detail_id")
    private Long detailId;
    /** 检验单ID(qc_iqc.iqc_id/qc_ipqc.ipqc_id/qc_oqc.oqc_id) */
    private Long checkId;
    /** 检验类别: IQC/IPQC/OQC */
    private String checkType;
    /** 来源模板ID(快照溯源) */
    private Long templateId;
    /** 来源检验项目ID */
    private Long itemId;
    /** 项目编码(快照) */
    private String itemCode;
    /** 项目名称(快照) */
    private String itemName;
    /** 数据类型(快照): QUANTITATIVE/QUALITATIVE */
    private String dataType;
    /** 标准类型(快照): NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL */
    private String standardType;
    /** 标准快照描述(如10±0.05mm) */
    private String standardDesc;
    /** 单位(快照) */
    private String unit;
    /** 关键度(快照): CRITICAL/MAJOR/MINOR */
    private String criticality;
    /** 样本量 */
    private BigDecimal sampleQty;
    /** 实测值(定量:数值/多值; 定性:OK/NG等) */
    private String measuredValue;
    /** 名义值(快照, 判定用) */
    private BigDecimal nominalValue;
    /** 下限(快照, 判定用) */
    private BigDecimal lslValue;
    /** 上限(快照, 判定用) */
    private BigDecimal uslValue;
    /** 上公差(快照, 判定用) */
    private BigDecimal tolUpper;
    /** 下公差(快照, 判定用) */
    private BigDecimal tolLower;
    /** 判定结果: PASS/FAIL/PENDING */
    private String judgeResult;
    /** 序号 */
    private Integer seqNum;
    /** 备注 */
    private String remark;
    @TableLogic
    private String delFlag;
}
