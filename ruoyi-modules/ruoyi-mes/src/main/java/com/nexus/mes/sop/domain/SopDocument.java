package com.nexus.mes.sop.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES SOP文档主档对象 sop_document
 * <p>属于 MES E-SOP 模块，表达 电子作业指导书主档。</p>
 * <p>状态机: DRAFT -> IN_REVIEW -> PUBLISHED -> ARCHIVED</p>
 * <p>版本管理: current_version/file_oss_id 由 doPublish 同步自 sop_version(CURRENT)</p>
 * <p>文件存储: file_oss_id (OSS私有桶存ID, VO用@Translation(OSS_ID_TO_URL)解析签名URL)</p>
 * <p>审批流: SopDocumentServiceImpl 实现 WfCallBackServiceI + 管理员兜底 approve/reject</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sop_document")
public class SopDocument extends TenantEntity {

    /** SOP ID(主键) */
    @TableId(value = "sop_id")
    private Long sopId;

    /** SOP编码 */
    private String sopCode;

    /** SOP名称 */
    private String sopName;

    /** SOP类型: PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN */
    private String sopType;

    /** 当前发布版本号 */
    private String currentVersion;

    /** 状态: DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED */
    private String status;

    /** 当前发布文件OSS ID(私有桶存ID, 对齐 EqPatrolRecord.photoOssId 用 String) */
    private String fileOssId;

    /** 文件类型: PDF/IMAGE/VIDEO/WORD */
    private String fileType;

    /** 说明 */
    private String description;

    /** 审批人ID */
    private Long approvalBy;

    /** 审批时间 */
    private Date approvalTime;

    /** 审批意见 */
    private String approvalRemark;

    /** 归档原因 */
    private String archiveReason;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
