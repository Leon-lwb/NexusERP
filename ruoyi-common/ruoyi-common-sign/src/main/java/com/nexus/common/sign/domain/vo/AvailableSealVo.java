package com.nexus.common.sign.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前用户可用印章。
 *
 * @author nexus
 */
@Data
public class AvailableSealVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 印章ID */
    private Long sealId;

    /** 印章类型(1个人 2公司) */
    private String sealType;

    /** 印章名称 */
    private String sealName;

    /** 预览图OSS ID（低清+水印；前端经 /system/sign/seal/{id}/preview 获取） */
    private Long previewOssId;

    /** 印章原图宽(px) */
    private Integer imgWidth;

    /** 印章原图高(px) */
    private Integer imgHeight;
}
