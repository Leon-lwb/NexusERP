package com.nexus.mes.pro.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * MES工序跳站业务对象(P0-3)
 * <p>承载 工序跳站(skip) 的申请人参。</p>
 * <p>跳站防错: 首道工序(sequenceNum=1)不允许跳过；须填写跳站原因；须有 mes:pro:flowcard:skip 权限。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProSkipBo extends BaseEntity {

    /** 流转卡ID */
    @NotNull(message = "流转卡不能为空")
    private Long cardId;

    /** 工序记录ID */
    @NotNull(message = "工序记录不能为空")
    private Long stepRecordId;

    /** 跳站原因 */
    @NotBlank(message = "跳站原因不能为空")
    private String reason;
}
