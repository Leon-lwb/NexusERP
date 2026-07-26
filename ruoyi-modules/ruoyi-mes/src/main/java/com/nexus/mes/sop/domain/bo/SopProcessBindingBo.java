package com.nexus.mes.sop.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.sop.domain.SopProcessBinding;

/**
 * 工序SOP绑定业务对象 sop_process_binding
 * <p>process_id/process_code/process_name/process_category 由前端工序选择器带入(冗余, 不跨库查ERP)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SopProcessBinding.class, reverseConvertGenerate = false)
public class SopProcessBindingBo extends BaseEntity {

    /** 绑定ID */
    private Long bindingId;

    /** 工序ID(ERP bd_process.process_id) */
    @NotNull(message = "工序不能为空")
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 工序名称(冗余) */
    private String processName;

    /** 工序类别(冗余, PROCESSING/ASSEMBLY/INSPECTION/PACKAGING) */
    private String processCategory;

    /** SOP ID */
    @NotNull(message = "SOP不能为空")
    private Long sopId;

    /** 指定版本号(空=当前发布版) */
    private String versionNo;

    /** 是否该工序默认SOP(1/0) */
    private String isDefault;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
