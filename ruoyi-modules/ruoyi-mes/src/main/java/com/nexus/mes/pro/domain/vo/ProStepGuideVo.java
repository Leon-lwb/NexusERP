package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProStepGuide;

import java.io.Serial;
import java.io.Serializable;

/**
 * MES工序流程指引配置视图对象 pro_step_guide
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProStepGuide.class)
public class ProStepGuideVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 指引ID */
    @ExcelProperty("指引ID")
    private Long guideId;

    /** 工序ID */
    private Long processId;

    /** 工序编码(冗余,前端选择器带入) */
    @ExcelProperty("工序编码")
    private String processCode;

    /** 工序名称(冗余,前端选择器带入) */
    @ExcelProperty("工序名称")
    private String processName;

    /** 指引类型: TEXT/SOP_LINK */
    @ExcelProperty("指引类型")
    private String guideType;

    /** 指引标题 */
    @ExcelProperty("指引标题")
    private String guideTitle;

    /** 文本内容(TEXT类型用) */
    private String guideContent;

    /** 关联SOP文档(SOP_LINK类型用) */
    private Long sopId;

    /** SOP文档名称(冗余,前端选择器带入,便于列表展示) */
    @ExcelProperty("SOP文档")
    private String sopName;

    /** 排序 */
    private Integer sortOrder;

    /** 是否启用(0停用 1启用) */
    @ExcelProperty("是否启用")
    private String enabled;
}
