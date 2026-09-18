package com.nexus.common.sign.domain.vo;

import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 待签文档正文视图对象（P2a-3）。
 *
 * <p><b>为什么同时给出 ossId 与 url：</b>{@code fileOssId}/{@code signedOssId} 是权威标识
 * （前端要下载就用它去调既有的 {@code /resource/oss/download/{ossId}}，与本模块
 * {@code EsSealController#preview} 返回 ossId 的做法一致）；{@code fileUrl}/{@code signedUrl}
 * 由 {@code @Translation} 在序列化时翻译，列表页可直接渲染，省一次往返。
 * 两者都不是「印章原图」那种敏感资产 —— 正文本来就是业务方自己要看的合同文本，
 * 且 OSS 下载端点自身有权限校验。</p>
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSignDocument.class)
public class EsSignDocumentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long docId;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 正文文件OSS ID（上传时的原始正文） */
    private Long fileOssId;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 上传时SM3值（验签比对基准） */
    private String fileHash;

    /** PDF页数 */
    private Integer pageCount;

    /** 正文版本（同一业务键每次上传 +1） */
    private Integer docVersion;

    /** 是否当前生效版本(1是 0否) */
    private String isCurrent;

    /** 签章后文件OSS ID（未签署为 null） */
    private Long signedOssId;

    /** 签署状态(0未签 1部分签 2已签) */
    private String signStatus;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /**
     * 原始正文访问地址（序列化时由 {@code fileOssId} 翻译）。
     */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "fileOssId")
    private String fileUrl;

    /**
     * 签署后正文访问地址（序列化时由 {@code signedOssId} 翻译；未签署时为 null）。
     */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "signedOssId")
    private String signedUrl;
}
