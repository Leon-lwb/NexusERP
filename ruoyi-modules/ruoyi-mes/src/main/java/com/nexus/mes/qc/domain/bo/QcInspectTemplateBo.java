package com.nexus.mes.qc.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.qc.domain.QcInspectTemplate;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * QC检验模板 业务对象
 * <p>items 子表随模板整体提交(先删后插)。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = QcInspectTemplate.class, reverseConvertGenerate = false)
public class QcInspectTemplateBo extends BaseEntity {
    /** 模板ID */
    private Long templateId;

    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @NotBlank(message = "模板类型不能为空")
    @Pattern(regexp = "IQC|IPQC|OQC", message = "模板类型必须为 IQC/IPQC/OQC")
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

    /** 检验项目列表(随模板一起保存) */
    private List<QcInspectItemBo> items;
}
