package com.nexus.common.sign.domain.bo;

import com.nexus.common.sign.domain.EsSeal;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 印章新增/修改入参。
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSeal.class, reverseConvertGenerate = false)
public class SealBo {

    private Long sealId;

    /** 印章类型(1个人 2公司) */
    @NotBlank(message = "印章类型不能为空")
    private String sealType;

    /** 印章名称 */
    @NotBlank(message = "印章名称不能为空")
    private String sealName;

    /** 个人章归属用户ID(个人章必填) */
    private Long ownerUserId;

    /** 印章原图OSS ID */
    @NotNull(message = "印章图片不能为空")
    private Long sealOssId;

    /** 印章原图宽 */
    @NotNull(message = "印章图片宽度不能为空")
    private Integer imgWidth;

    /** 印章原图高 */
    @NotNull(message = "印章图片高度不能为空")
    private Integer imgHeight;

    /** 生效日期 */
    private Date validFrom;

    /** 失效日期 */
    private Date validTo;

    private String remark;
}
