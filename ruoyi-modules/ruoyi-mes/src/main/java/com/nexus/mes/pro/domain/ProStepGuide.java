package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES工序流程指引配置对象 pro_step_guide
 * <p>属于 MES 生产管理模块(P0-3)，表达 工序级作业指引 的领域模型。</p>
 * <p>指引类型收敛为 TEXT(文本) + SOP_LINK(关联SOP文档) 两类；
 * 富媒体作业指导统一走 P0-5 sop_document(版本+审批+审计)，本表用 SOP_LINK 引用。</p>
 * <p>SOP_LINK 类型时 sop_id 须在 sop_process_binding 已绑定到同 process_id。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_step_guide")
public class ProStepGuide extends TenantEntity {

    /** 指引ID(主键) */
    @TableId(value = "guide_id")
    private Long guideId;

    /** 工序ID(ERP bd_process.process_id) */
    private Long processId;

    /** 指引类型: TEXT/SOP_LINK */
    private String guideType;

    /** 指引标题 */
    private String guideTitle;

    /** 文本内容(TEXT类型用) */
    private String guideContent;

    /** 关联SOP文档(SOP_LINK类型用,须在sop_process_binding已绑定同process_id) */
    private Long sopId;

    /** 排序 */
    private Integer sortOrder;

    /** 是否启用(0停用 1启用) */
    private String enabled;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
