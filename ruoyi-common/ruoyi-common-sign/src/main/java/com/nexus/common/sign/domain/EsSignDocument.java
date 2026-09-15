package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 待签文档正文。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("es_sign_document")
public class EsSignDocument extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档ID */
    @TableId(value = "doc_id")
    private Long docId;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 正文文件OSS ID(仅PDF) */
    private Long fileOssId;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 上传时SM3值(验签比对基准) */
    private String fileHash;

    /** PDF页数 */
    private Integer pageCount;

    /** 正文版本 */
    private Integer docVersion;

    /** 是否当前生效版本(1是 0否) */
    private String isCurrent;

    /** 签章后文件OSS ID */
    private Long signedOssId;

    /** 签署状态(0未签 1部分签 2已签) */
    private String signStatus;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
