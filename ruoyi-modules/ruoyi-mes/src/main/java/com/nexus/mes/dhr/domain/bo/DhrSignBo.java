package com.nexus.mes.dhr.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DHR 批记录签署业务对象（提交/放行/驳回共用）。
 *
 * <p>二级认证凭证走请求体而非 URL query —— query 会留痕访问日志/浏览器历史，
 * 且被操作日志切面明文落库（Controller 侧已用 {@code excludeParamNames = {"credential"}} 排除）。</p>
 *
 * <p>{@code authMethod} 固定为 {@code PASSWORD}（前端传登录密码）；
 * {@code LOGIN_ONLY} 会被服务层拒绝。</p>
 *
 * @author Nexus
 */
@Data
public class DhrSignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 认证方式（见 SignAuthMethod；DHR 不接受 LOGIN_ONLY） */
    @NotBlank(message = "认证方式不能为空")
    private String authMethod;

    /** 二级认证凭证（PASSWORD 时为登录密码） */
    private String credential;

    /** 备注（提交/放行时可选写入日志语义） */
    private String remark;

    /** 驳回原因（仅 reject 使用） */
    private String reason;
}
