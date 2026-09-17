package com.nexus.common.sign.domain.bo;

import com.nexus.common.sign.domain.dto.SealPlacement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文档签章入参（{@code POST /system/sign/document/{docId}/sign} 的请求体）。
 *
 * <p><b>为什么没有 bizType / bizId：</b>业务键一律由服务端从 {@code es_sign_document}
 * 行里取（见 {@code EsSignDocumentController#sign}）。让客户端传业务键，
 * 就等于把「章盖到哪张单据上」交给客户端决定 —— 即便 {@code signDocument} 会做一致性
 * 校验，也没有必要把一个必然被拒的入参暴露出去。</p>
 *
 * <p>坐标语义见 {@link SealPlacement}（归一化 0..1、原点左上）。</p>
 *
 * @author nexus
 */
@Data
public class DocSignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 印章落位列表（必填且非空，可含多页多章） */
    @NotEmpty(message = "印章落位不能为空")
    private List<SealPlacement> placements;

    /** 签署含义（必填，写入 PDF 签名的 /Reason） */
    @NotBlank(message = "签署含义不能为空")
    private String signatureMeaning;

    /** 认证方式（必填，见 {@code SignAuthMethod}） */
    @NotBlank(message = "认证方式不能为空")
    private String authMethod;

    /** 二级认证凭证（{@code LOGIN_ONLY} 时可为空） */
    private String credential;
}
