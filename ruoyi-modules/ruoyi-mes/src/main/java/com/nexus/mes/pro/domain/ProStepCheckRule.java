package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES工序校验规则配置对象 pro_step_check_rule
 * <p>属于 MES 生产管理模块(P0-3)，表达 工序进站/出站校验规则 的领域模型。</p>
 * <p>check_type: 校验类型(9种)；check_stage: ENTRY(进站)/EXIT(出站)；enabled: 启用开关。</p>
 * <p>规则引擎 IProEntryCheckService 按 process_id 加载 enabled 规则并执行。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_step_check_rule")
public class ProStepCheckRule extends TenantEntity {

    /** 规则ID(主键) */
    @TableId(value = "rule_id")
    private Long ruleId;

    /** 工序ID */
    private Long processId;

    /** 校验类型: MATERIAL_KITTING/OPERATOR_SKILL/EQUIPMENT_STATUS/SOP_VERSION/TOOLING_AVAILABLE/LOT_AVAILABLE/INSPECTION_REQUIRED/QUANTITY_REACHED/PARAMETER_OK/EXCEPTION_CLOSED */
    private String checkType;

    /** 校验阶段: ENTRY/EXIT */
    private String checkStage;

    /** 是否启用(0停用 1启用) */
    private String enabled;

    /** 校验失败提示 */
    private String errorMsg;

    /** 排序 */
    private Integer sortOrder;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
