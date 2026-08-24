package com.nexus.erp.sch.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * App 补卡申请入参对象
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
public class SchSupplementApplyBo {

    /** 补卡日期，格式 yyyy-MM-dd */
    @NotBlank(message = "补卡日期不能为空")
    private String applyDate;

    /** 补卡类型: clock_in上班缺卡 / clock_out下班缺卡 / full_day全天缺卡 */
    @NotBlank(message = "补卡类型不能为空")
    private String applyType;

    /** 补卡原因 */
    @NotBlank(message = "补卡原因不能为空")
    private String reason;
}
