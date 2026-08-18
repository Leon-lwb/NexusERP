package com.nexus.mes.flash.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES固件档案对象 mes_firmware
 * <p>固件主档: 按物料多版本, 文件存 MinIO(OSS 存 ID), MD5/大小随上传回填。</p>
 * <p>状态机: DRAFT -> IN_REVIEW -> RELEASED -> ARCHIVED</p>
 * <p>约束: uk_tenant_firmware_code 编码唯一; is_default 物料内唯一(仅 RELEASED 可设默认); 归档/删除受绑定与审批约束。</p>
 * <p>审批流: FirmwareServiceImpl 实现 WfCallBackServiceI + 管理员兜底 approve/reject</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_firmware")
public class Firmware extends TenantEntity {

    /** 固件ID(主键) */
    @TableId(value = "firmware_id")
    private Long firmwareId;

    /** 固件编码(自动编码 MES_FW_CODE) */
    private String firmwareCode;

    /** 固件名称 */
    private String firmwareName;

    /** 适用物料ID(产品) */
    private Long materialId;

    /** 适用物料编码 */
    private String materialCode;

    /** 适用物料名称 */
    private String materialName;

    /** 版本号(如 1.2.3) */
    private String version;

    /** 构建号 */
    private String buildNo;

    /** 固件文件OSS文件ID(sys_oss) */
    private Long ossId;

    /** 固件文件MD5 */
    private String fileMd5;

    /** 固件文件大小(字节) */
    private Long fileSize;

    /** 发布说明 */
    private String releaseNote;

    /** 状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED */
    private String status;

    /** 是否物料默认版本(0否/1是, 物料内唯一) */
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