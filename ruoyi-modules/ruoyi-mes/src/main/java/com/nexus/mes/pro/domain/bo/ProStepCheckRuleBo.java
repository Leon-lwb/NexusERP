package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProStepCheckRule;

/**
 * MES工序校验规则配置业务对象 pro_step_check_rule
 * <p>承载 工序校验规则 的增改入参与查询条件。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProStepCheckRule.class, reverseConvertGenerate = false)
public class ProStepCheckRuleBo extends BaseEntity {

    /** 规则ID */
    private Long ruleId;

    /** 工序ID */
    @NotNull(message = "工序不能为空")
    private Long processId;

    /** 校验类型 */
    @NotBlank(message = "校验类型不能为空")
    private String checkType;

    /** 校验阶段: ENTRY/EXIT */
    @NotBlank(message = "校验阶段不能为空")
    private String checkStage;

    /** 是否启用(0停用 1启用) */
    private String enabled;

    /** 校验失败提示 */
    private String errorMsg;

    /** 排序 */
    private Integer sortOrder;

    /** 批量删除ID集合 */
    private Long[] ids;
}
