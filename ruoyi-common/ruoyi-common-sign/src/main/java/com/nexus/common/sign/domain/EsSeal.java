package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Date;

/**
 * 电子印章。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("es_seal")
public class EsSeal extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 印章ID */
    @TableId(value = "seal_id")
    private Long sealId;

    /** 印章类型(1个人章 2公司章) */
    private String sealType;

    /** 印章名称 */
    private String sealName;

    /** 个人章归属用户ID(公司章为NULL) */
    private Long ownerUserId;

    /** 印章原图OSS ID(私有桶,仅服务端使用) */
    private Long sealOssId;

    /** 预览图OSS ID(低清+水印,前端可见) */
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

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
