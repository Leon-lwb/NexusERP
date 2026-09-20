package com.nexus.mes.qc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspect_template")
public class QcInspectTemplate extends TenantEntity {
    @TableId(value = "template_id")
    private Long templateId;
    /** 模板编码(唯一) */
    private String templateCode;
    /** 模板名称 */
    private String templateName;
    /** 模板类型: IQC/IPQC/OQC */
    private String templateType;
    /** 物料ID(空=通用模板) */
    private Long materialId;
    /** 物料编码(冗余) */
    private String materialCode;
    /** 物料名称(冗余) */
    private String materialName;
    /** 工序ID(IPQC 维度, 空=不限工序) */
    private Long processId;
    /** 客户ID(客户专属模板维度, 空=不限客户) */
    private Long customerId;
    /** 版本号 */
    private Integer versionNo;
    /** 状态: DRAFT/ACTIVE */
    private String status;
    /** 备注 */
    private String remark;
    @TableLogic
    private String delFlag;
}
