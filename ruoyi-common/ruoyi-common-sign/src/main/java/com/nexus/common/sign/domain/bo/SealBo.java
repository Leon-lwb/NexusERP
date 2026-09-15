package com.nexus.common.sign.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.sign.domain.EsSeal;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 印章新增/修改入参。
 *
 * <p><b>校验分组</b>：印章图片相关字段（{@code sealOssId}/{@code imgWidth}/{@code imgHeight}）
 * 仅在<b>新增</b>（{@code AddGroup}）时必填 —— 修改时前端不回传图片表示「沿用原章面」
 * （{@link com.nexus.common.sign.domain.vo.SealVo} 不携带 {@code sealOssId}）。
 * {@code sealType}/{@code sealName} 则声明为 {@code AddGroup + EditGroup} 双分组必填，
 * 不可留空 —— 无分组注解的约束只属于 {@code Default} 组，
 * 分组校验时不会被执行（服务层 {@code validateSealBo} 依赖这两个字段）。</p>
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSeal.class, reverseConvertGenerate = false)
public class SealBo {

    private Long sealId;

    /** 印章类型(1个人 2公司) */
    @NotBlank(message = "印章类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String sealType;

    /** 印章名称 */
    @NotBlank(message = "印章名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String sealName;

    /** 个人章归属用户ID(个人章必填) */
    private Long ownerUserId;

    /** 印章原图OSS ID（新增必填；修改留空表示沿用原章面） */
    @NotNull(message = "印章图片不能为空", groups = AddGroup.class)
    private Long sealOssId;

    /** 印章原图宽（新增必填；修改仅在更换图片时由服务层要求） */
    @NotNull(message = "印章图片宽度不能为空", groups = AddGroup.class)
    private Integer imgWidth;

    /** 印章原图高（新增必填；修改仅在更换图片时由服务层要求） */
    @NotNull(message = "印章图片高度不能为空", groups = AddGroup.class)
    private Integer imgHeight;

    /** 生效日期 */
    private Date validFrom;

    /** 失效日期 */
    private Date validTo;

    private String remark;
}
