package com.nexus.mes.eq.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MES 备件编码纠正请求。
 * <p>当前编码用于并发 CAS 校验，纠正原因随操作日志留痕；服务端统一按
 * {@code MES_SPARE_CODE} 规则生成新编码。</p>
 *
 * @author Nexus
 */
@Data
public class EqSpareCodeCorrectionBo {

    /** 调用方读取到的当前备件编码 */
    @NotBlank(message = "当前备件编码不能为空")
    @Size(max = 64, message = "当前备件编码不能超过64个字符")
    private String expectedCurrentCode;

    /** 编码纠正原因 */
    @NotBlank(message = "编码纠正原因不能为空")
    @Size(max = 500, message = "编码纠正原因不能超过500个字符")
    private String reason;
}
