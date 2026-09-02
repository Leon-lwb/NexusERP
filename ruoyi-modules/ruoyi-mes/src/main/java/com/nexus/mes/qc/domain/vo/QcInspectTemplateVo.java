package com.nexus.mes.qc.domain.vo;

import com.nexus.mes.qc.domain.QcInspectTemplate;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * QC检验模板 视图对象
 */
@Data
@AutoMapper(target = QcInspectTemplate.class)
public class QcInspectTemplateVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long templateId;
    /** 模板编码 */
    private String templateCode;
    /** 模板名称 */
    private String templateName;
    /** 模板类型: IQC/IPQC/OQC */
    private String templateType;
    /** 物料ID(空=通用模板) */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 版本号 */
    private Integer versionNo;
    /** 状态: DRAFT/ACTIVE */
    private String status;
    /** 备注 */
    private String remark;

    /** 检验项目列表 */
    private List<QcInspectItemVo> items;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
