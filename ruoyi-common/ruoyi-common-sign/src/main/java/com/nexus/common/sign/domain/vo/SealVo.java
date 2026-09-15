package com.nexus.common.sign.domain.vo;

import com.nexus.common.sign.domain.EsSeal;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 印章视图对象。
 * <p>注意：<b>不包含</b> sealOssId（印章原图）—— 原图绝不下发前端。</p>
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSeal.class)
public class SealVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 印章ID */
    private Long sealId;

    /** 印章类型(1个人 2公司) */
    private String sealType;

    /** 印章名称 */
    private String sealName;

    /** 个人章归属用户ID(公司章为NULL) */
    private Long ownerUserId;

    /** 预览图OSS ID（低清+水印；前端经 /system/sign/seal/{id}/preview 获取） */
    private Long previewOssId;

    /** 印章原图宽(px) */
    private Integer imgWidth;

    /** 印章原图高(px) */
    private Integer imgHeight;

    /** 状态(0草稿 1启用 2停用 3销毁) */
    private String status;

    /** 印章版本 */
    private Integer sealVersion;

    /** 生效日期 */
    private Date validFrom;

    /** 失效日期 */
    private Date validTo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
