package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 电子签名记录。
 * <p><b>append-only</b>：有意不继承 {@code BaseEntity}/{@code TenantEntity}，
 * 不提供 update_by/update_time/del_flag 字段，使"改写"在实体层面无从表达。
 * tenant_id 由 MyBatis-Plus 多租户插件在 SQL 层自动注入。</p>
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@TableName("es_signature")
public class EsSignature implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 签名ID */
    @TableId(value = "signature_id")
    private Long signatureId;

    /** 租户ID(由多租户插件在 SQL 层注入) */
    private String tenantId;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 业务单据编号(冗余快照) */
    private String bizNo;

    /** 签署人用户ID */
    private Long signerUserId;

    /** 签署人姓名(冗余快照) */
    private String signerName;

    /** 签署人部门ID(快照) */
    private Long signerDeptId;

    /** 所用印章ID */
    private Long sealId;

    /** 印章类型快照 */
    private String sealType;

    /** 签署含义 */
    private String signatureMeaning;

    /** 认证方式 */
    private String authMethod;

    /** 签署前文档OSS ID */
    private Long srcDocOssId;

    /** 签署前文档SM3 */
    private String srcDocHash;

    /** 签署后文档OSS ID */
    private Long docOssId;

    /** 签署后文档SM3 */
    private String docHash;

    /** 关联 es_sign_document.doc_id */
    private Long docId;

    /** 签署时间(服务端时间) */
    private Date signedAt;

    /** 签署IP */
    private String signIp;

    /** 签署User-Agent */
    private String signUa;

    /** 前一条链哈希(链首为NULL) */
    private String prevHash;

    /** 本条链哈希(SM3) */
    private String chainHash;

    /** 备注 */
    private String remark;
}
