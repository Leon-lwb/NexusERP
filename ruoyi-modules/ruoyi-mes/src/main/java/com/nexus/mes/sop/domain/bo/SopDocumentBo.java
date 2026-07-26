package com.nexus.mes.sop.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.sop.domain.SopDocument;

/**
 * MES SOP文档业务对象 sop_document
 * <p>承载 SOP文档 的增改入参与查询条件。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SopDocument.class, reverseConvertGenerate = false)
public class SopDocumentBo extends BaseEntity {

    /** SOP ID */
    private Long sopId;

    /** SOP编码(留空则自动生成 MES_SOP_CODE) */
    private String sopCode;

    /** SOP名称 */
    @NotBlank(message = "SOP名称不能为空")
    private String sopName;

    /** SOP类型: PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN */
    @NotBlank(message = "SOP类型不能为空")
    private String sopType;

    /** 当前发布版本号(查询用, 新建时不填) */
    private String currentVersion;

    /** 状态(查询用, 新建默认 DRAFT) */
    private String status;

    /** 首版本文件OSS ID(新建SOP时同时建首版本) */
    private String fileOssId;

    /** 文件类型: PDF/IMAGE/VIDEO/WORD */
    private String fileType;

    /** 说明 */
    private String description;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
