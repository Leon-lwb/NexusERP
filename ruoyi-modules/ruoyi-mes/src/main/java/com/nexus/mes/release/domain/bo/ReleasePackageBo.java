package com.nexus.mes.release.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.release.domain.ReleasePackage;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * MES客户定制化发布包业务对象 mes_release_package
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReleasePackage.class, reverseConvertGenerate = false)
public class ReleasePackageBo extends BaseEntity {

    /** 发布包ID */
    @Null(groups = {AddGroup.class})
    @NotNull(groups = {EditGroup.class})
    private Long packageId;

    /** 发布包编码(查询用;新增留空自动生成 MES_RP_CODE) */
    private String packageCode;

    /** 客户ID */
    @NotNull(message = "客户不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long clientId;

    /** 客户名称(冗余) */
    private String clientName;

    /** 成品物料ID */
    @NotNull(message = "适用物料不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 客户产品代号(匹配键) */
    private String customerProductCode;

    /** 客户产品名称 */
    private String customerProductName;

    /** 版本号(如 2.1) */
    @NotBlank(message = "版本号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String version;

    /** 发布说明 */
    private String releaseNotes;

    /** 状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED(查询用) */
    private String status;

    /** 生效日期(空=发布即生效) */
    private Date effectiveDate;

    /** 过期日期(空=永久有效) */
    private Date expiryDate;

    /** 备注 */
    private String remark;

    // ==================== 子表(随主表一起保存: 先删后插) ====================

    /** 固件映射列表 */
    private List<ReleasePkgFirmwareBo> firmwareMappings;

    /** BOM选配列表 */
    private List<ReleasePkgBomItemBo> bomItems;

    /** 配置项列表 */
    private List<ReleasePkgConfigBo> configItems;
}
