package com.nexus.mes.qc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspect_item")
public class QcInspectItem extends TenantEntity {
    @TableId(value = "item_id")
    private Long itemId;
    /** 关联模板ID */
    private Long templateId;
    /** 项目编码 */
    private String itemCode;
    /** 项目名称 */
    private String itemName;
    /** 数据类型: QUANTITATIVE定量/QUALITATIVE定性 */
    private String dataType;
    /** 计量单位 */
    private String unit;
    /** 标准类型: NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL */
    private String standardType;
    /** 名义值(NOMINAL用) */
    private BigDecimal nominalValue;
    /** 下规格限LSL */
    private BigDecimal lslValue;
    /** 上规格限USL */
    private BigDecimal uslValue;
    /** 上公差(NOMINAL用, 名义+tolUpper) */
    private BigDecimal tolUpper;
    /** 下公差(NOMINAL用, 名义-tolLower) */
    private BigDecimal tolLower;
    /** 检验水平(如II/III; 当前仅用于抽样量计算, Ac/Re 判定未接入) */
    private String aqlLevel;
    /** 抽样方案(如AQL1.0; 当前仅用于抽样量计算, Ac/Re 判定未接入) */
    private String samplePlanCode;
    /** 关键度: CRITICAL/MAJOR/MINOR */
    private String criticality;
    /** 序号 */
    private Integer seqNum;
    /** 是否启用 1是0否 */
    private String enabled;
    /** 备注 */
    private String remark;
    @TableLogic
    private String delFlag;
}
