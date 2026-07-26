package com.nexus.mes.sop.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.sop.domain.SopProcessBinding;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 工序SOP绑定视图对象 sop_process_binding
 * <p>sopName/sopCode 由 Service 联表填充。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SopProcessBinding.class)
public class SopProcessBindingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 绑定ID */
    private Long bindingId;

    /** 工序ID(ERP bd_process.process_id) */
    @ExcelProperty("工序ID")
    private Long processId;

    /** 工序编码(冗余) */
    @ExcelProperty("工序编码")
    private String processCode;

    /** 工序名称(冗余) */
    @ExcelProperty("工序名称")
    private String processName;

    /** 工序类别(冗余, PROCESSING/ASSEMBLY/INSPECTION/PACKAGING) */
    @ExcelProperty("工序类别")
    private String processCategory;

    /** SOP ID */
    private Long sopId;

    /** SOP编码(Service 联表填充) */
    @ExcelProperty("SOP编码")
    private String sopCode;

    /** SOP名称(Service 联表填充) */
    @ExcelProperty("SOP名称")
    private String sopName;

    /** SOP状态(Service 联表填充) */
    private String sopStatus;

    /** 指定版本号(空=当前发布版) */
    @ExcelProperty("指定版本")
    private String versionNo;

    /** 是否该工序默认SOP(1/0) */
    @ExcelProperty("默认")
    private String isDefault;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;
}
