package com.nexus.mes.ncr.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * NCR「签署并处置」请求体（PUT /mes/ncr/order/dispose/{ncrId}/sign）。
 *
 * <p><b>为什么改走请求体：</b>二级认证凭证（登录密码）曾以 {@code @RequestParam} 经 URL query 传输，
 * 会留痕在访问日志/代理日志/浏览器历史，并被操作日志切面按 query 参数整体 JSON 化写入
 * {@code sys_oper_log.oper_param}。改走请求体后 query 为空，日志切面走对象序列化路径，
 * {@code credential} 由全局排除清单（{@code SystemConstants.EXCLUDE_PROPERTIES}）过滤。</p>
 *
 * @author Nexus
 */
@Data
public class NcrDisposeSignBo {

    /** 处置方式（REWORK/SCRAP/ACCEPT_AS_IS/RETURN） */
    @NotBlank(message = "处置方式不能为空")
    private String disposition;

    /** 处置备注（可空） */
    private String remark;

    /** 二级认证方式（LOGIN_ONLY 将被服务层拒绝：处置必须二级认证） */
    @NotBlank(message = "认证方式不能为空")
    private String authMethod;

    /** 二级认证凭证（登录密码；不落操作日志） */
    @NotBlank(message = "认证凭证不能为空")
    private String credential;
}
