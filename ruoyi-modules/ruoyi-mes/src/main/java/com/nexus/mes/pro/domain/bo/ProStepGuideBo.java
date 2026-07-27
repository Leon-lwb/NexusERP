package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProStepGuide;

/**
 * MES工序流程指引配置业务对象 pro_step_guide
 * <p>承载 工序流程指引 的增改入参与查询条件。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProStepGuide.class, reverseConvertGenerate = false)
public class ProStepGuideBo extends BaseEntity {

    /** 指引ID */
    private Long guideId;

    /** 工序ID(ERP bd_process.process_id) */
    @NotNull(message = "工序不能为空")
    private Long processId;

    /** 指引类型: TEXT/SOP_LINK */
    @NotBlank(message = "指引类型不能为空")
    private String guideType;

    /** 指引标题 */
    private String guideTitle;

    /** 文本内容(TEXT类型用) */
    private String guideContent;

    /** 关联SOP文档(SOP_LINK类型用) */
    private Long sopId;

    /** 排序 */
    private Integer sortOrder;

    /** 是否启用(0停用 1启用) */
    private String enabled;

    /** 批量删除ID集合 */
    private Long[] ids;
}
