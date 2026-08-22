package com.nexus.mes.release.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES客户定制化发布包对象 mes_release_package
 * <p>发布包 = 客户+产品维度的定制化生产配置集合(固件映射/BOM选配/配置项),
 * 在标准工艺路线与标准BOM之上做增量覆写, 不修改原始主数据。</p>
 * <p>状态机: DRAFT -> IN_REVIEW -> RELEASED -> ARCHIVED
 * <p>约束: uk_tenant_package_code 编码唯一; 同客户+物料+版本唯一由 Service 校验;
 * is_default 客户+物料内唯一(仅 RELEASED 可设默认)。</p>
 * <p>审批流: ReleasePackageServiceImpl 实现 WfCallBackServiceI + 管理员兜底 approve/reject</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_release_package")
public class ReleasePackage extends TenantEntity {

    /** 发布包ID(主键) */
    @TableId(value = "package_id")
    private Long packageId;

    /** 发布包编码(自动编码 MES_RP_CODE) */
    private String packageCode;

    /** 客户ID(bd_client.client_id) */
    private Long clientId;

    /** 客户名称(冗余) */
    private String clientName;

    /** 成品物料ID(bd_material.material_id) */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 客户产品代号(客户对该产品的内部编号, 匹配键) */
    private String customerProductCode;

    /** 客户产品名称 */
    private String customerProductName;

    /** 版本号(如 2.1) */
    private String version;

    /** 发布说明 */
    private String releaseNotes;

    /** 状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED */
    private String status;

    /** 生效日期(空=发布即生效) */
    private Date effectiveDate;

    /** 过期日期(空=永久有效) */
    private Date expiryDate;

    /** 是否客户+物料默认发布包(0否/1是, 客户+物料内唯一) */
    private String isDefault;

    /** 审批流配置ID(wf_custom_form.id) */
    private Long selectFlowId;

    /** 发布人ID */
    private Long publishedBy;

    /** 发布时间 */
    private Date publishTime;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
