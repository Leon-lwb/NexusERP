package com.nexus.mes.sop.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工序SOP绑定对象 sop_process_binding
 * <p>process_id = ERP bd_process.process_id (工序主数据, 跨工艺路线复用)</p>
 * <p>冗余 process_code/process_name/process_category (前端选择器带入, 不跨库查ERP)</p>
 * <p>is_default=1 时同工序其他绑定排他置0 (CAS)</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sop_process_binding")
public class SopProcessBinding extends TenantEntity {

    /** 绑定ID(主键) */
    @TableId(value = "binding_id")
    private Long bindingId;

    /** 工序ID(ERP bd_process.process_id, 工序主数据PK) */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 工序名称(冗余) */
    private String processName;

    /** 工序类别(冗余, PROCESSING/ASSEMBLY/INSPECTION/PACKAGING) */
    private String processCategory;

    /** SOP ID */
    private Long sopId;

    /** 指定版本号(空=当前发布版) */
    private String versionNo;

    /** 是否该工序默认SOP(1/0) */
    private String isDefault;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
