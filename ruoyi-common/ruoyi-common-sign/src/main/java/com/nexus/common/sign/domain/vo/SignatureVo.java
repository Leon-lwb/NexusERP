package com.nexus.common.sign.domain.vo;

import com.nexus.common.sign.domain.EsSignature;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 签名记录视图对象。
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSignature.class)
public class SignatureVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 签名ID */
    private Long signatureId;

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
